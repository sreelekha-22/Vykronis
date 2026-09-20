# Local Demo Walkthrough — Running Vykronis End-to-End (Compose)

> Session log. Date: 2026-09-15. Machine: Windows + Docker Desktop (WSL2).
> Status legend: `[x]` done · `[ ]` pending · `[~]` in progress.

## What this demo shows

Vykronis is an **autonomous reliability loop**. This walkthrough runs it for real on
Docker Compose: a fault is injected into a watched "demo app", the platform detects,
correlates, incidents, investigates, policy-gates, remediates, verifies, and learns —
with no human touching the fix.

**The watched apps (simulated):** `order-service`, `payment-service`,
`inventory-service`, `notification-service`. A fault generator streams their metrics
into Kafka; the platform acts on them.

## The pipeline in one line

```
metrics → INGEST → DETECT (1-min window) → CORRELATE (blame deployment) → INCIDENT
       → INVESTIGATE (hypothesis) → POLICY (gate) → REMEDIATE (executor)
       → VERIFY (60s window) → LEARN (remediation_learn)
```

---

## Step 1 — Start core infrastructure (Kafka, Postgres, Redis)  `[x]`

```bash
cd D:\Java_Pro_Projects\Latest_Pro\Obs_Autonomous_System\Ob_Autonomous_Platform
docker compose -f infra/compose/docker-compose.yml up -d
docker compose -f infra/compose/docker-compose.yml ps
```

**Observed:** `kafka`, `postgres`, `redis` started; after ~60 s all three report
`healthy`.

> Housekeeping performed during the session: removed the leftover `kind` cluster
> node (`vykronis-control-plane`) that was burning ~40% CPU.

**Ports so far:** Kafka 9092 · Postgres 5432 · Redis 6379

---

## Step 2 — Start the 9 platform services  `[x]`

```bash
docker compose -f infra/compose/docker-compose.yml \
               -f infra/compose/docker-compose.apps.yml \
               --profile apps up -d
```

The overlay starts: api-gateway (:8080), ingestion (:8081), event (:8082),
correlation-engine (:8083), incident-service (:8084), agent-orchestrator (:8085),
policy-service (:8086), remediation-service (:8087), schema-registry (:8090).

**Observed (2026-09-17):** all 9 services healthy; kafka/postgres/redis healthy.
Fixes applied during the session (see Session notes below): app overlay DB/Kafka
env wiring, `-Xmx256m` caps, kafka advertised listener, and a self-healing
correlation-engine topic bootstrap.

## Step 3 — Health check  `[x]`

```bash
curl -s http://localhost:8080/actuator/health   # api-gateway
curl -s http://localhost:8083/actuator/health   # correlation-engine
curl -s http://localhost:8084/actuator/health   # incident-service
```

All report `{"status":"UP"}`.

## Step 4 — Seed a deployment ("version 1.4.2 goes live")  `[x]`

```bash
curl -X POST "http://localhost:8085/api/demo/deploy/payment-service?version=1.4.2&env=PROD"
# {"env":"PROD","deploymentId":"63ed9372-9015-4088-832f-d5caf8c4ae26","serviceId":"payment-service","version":"1.4.2"}
```

Purpose: gives the correlation engine something to **blame** the incident on.
Verified 2026-09-17: `obs.deployments` carries
`{"deploymentId":"63ed9372-...","serviceId":"payment-service","version":"1.4.2",
"status":"SUCCESS"}` — the topic now self-bootstraps (see gotchas).

## Step 5 — Start the fault generator (the demo app talking)  `[x]`

The generator runs **inside** the agent-orchestrator container — just set the
compose env and `up -d` (host `spring-boot:run` doesn't work here: port 8085 is
taken by the container and the host has no path to the bridged Kafka):

```yaml
# infra/compose/docker-compose.apps.yml, agent-orchestrator service:
environment:
  SPRING_PROFILES_ACTIVE: demo-traffic
```

Emits a metric every 500 ms; every 12th cycle injects an error burst on
`payment-service` (error_rate 45-65%): `demo-traffic` profile, `DemoRunner`.

## Step 6 — Watch metrics being ingested  `[x]`

```bash
docker exec vykronis-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic obs.metrics --from-beginning --max-messages 5
```

**Observed:** healthy samples (~0.5-3.5% error) interleaved with the burst
(`err=62.4% cnt=6` on `payment-service`).

## Step 7 — Confirm event-service persisted them  `[~]` (optional)

> Requires the OpenSearch `search` profile (`docker compose ... --profile search
> up -d`, ~+512 MB). Not part of the Phase 6 core loop, so skipped on this box.

```bash
curl "http://localhost:8082/api/search/events?from=2026-09-17T00:00:00Z&to=2026-09-18T00:00:00Z&serviceId=payment-service&type=METRIC"
```

(Note: the real path is `/api/search/events` with `from`/`to` instants, not
`/api/events`. Without OpenSearch the service logs "OpenSearch unavailable" and
the endpoint returns 500.)

## Step 8 — Correlation fires (window breach → candidate)  `[x]`

There is no HTTP API on :8083 — the engine emits candidates onto Kafka topic
`obs.alerts`:

```bash
docker exec vykronis-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic obs.alerts --from-beginning --max-messages 2
```

**Observed:** `{"serviceId":"payment-service","env":"PROD","severity":"CRITICAL",
"reason":"error_rate 64.97 >= 20.0","windowStart":...,"windowEnd":...}`.

Rule: peak error_rate ≥ 20% **OR** cumulative error_count ≥ 5 in a 1-minute
tumbling window; candidate is attributed to deployment 1.4.2.

## Step 9 — Incident appears (OPEN)  `[x]`

```bash
curl http://localhost:8084/api/incidents
```

**Observed (2026-09-17):** `19f05ac8-f36c-419c-8116-e4ae3cdc96b7` —
`payment-service` CRITICAL PROD, window 17:32–17:41, `error_rate_max 64.97%`,
`error_count 177`, status **OPEN**. (Two other incidents are seeded test rows
from 2026-09-05.)

Also visible in the UI: **http://localhost:8080/incidents**

## Step 10 — Investigate (agent forms hypothesis)  `[x]`

```bash
curl -X POST http://localhost:8084/api/incidents/<ID>/investigate \
  -H "Content-Type: application/json" \
  -d '{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
```

Status → `HYPOTHESIS_READY`; stores hypothesis + evidence on the incident.
Verified 2026-09-17 on incident `1cabf404`: hypothesis
`"Elevated error/latency most plausibly originating from payment-service"`,
confidence 0.5, **120 evidence items**.

> Note: the subject body is a record `OperatorActor(String name, Set<String> roles,
> boolean service)` — the `service` boolean is **required** and the approver role is
> **`vykronis-approver`** (a `DecisionMatrix` constant), not `approver`.

## Step 11 — Request remediation (policy gate)  `[x]`

```bash
curl -X POST http://localhost:8084/api/incidents/<ID>/remediation \
  -H "Content-Type: application/json" \
  -d '{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
```

PROD + high severity ⇒ `AWAITING_APPROVAL` / `REQUIRE_APPROVAL` (DEV ⇒ auto
`ALLOW`). Verified: invoice-city PROD rollback required approval; an unknown role
(`"approver"`) was **`DENY`** (fail-closed). Requires incident-service env
`VYKRONIS_POLICY_URL=http://policy-service:8086` (default `localhost:8086` =
itself → 503).

## Step 12 — Approve (executor runs the fix)  `[x]`

```bash
curl -X POST http://localhost:8084/api/incidents/<ID>/approve \
  -H "Content-Type: application/json" \
  -d '{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
```

Verified: status → `REMEDIATING`, `RemediationCommand{id=ee375fdf…, action=ROLLBACK,
service=payment-service, env=PROD}` published to `obs.remediation` (offset 1).
The remediation-service consumer (group `remediation-service2`) executed it against
the host docker daemon through the mounted socket: `ComposeExecutor` ran
`docker compose up -d --no-deps payment-service` → **`COMPLETED`** with
`detail="Container vykronis-payment-service Running"`, stored in
`remediation_records` and published back to `obs.remediation`.

(Executor wiring: `/var/run/docker.sock` mount + docker CLI/compose plugin baked
into the image by `platform/remediation-service/Dockerfile.hostbuild`; also see
gotchas — the two source-level fixes that make the listener actually run.)

## Step 13 — Verification window  `[x]`

```bash
curl http://localhost:8084/api/incidents/<ID> | jq '{status,remediationOutcome}'
```

With the executor wired (Step 12), the result consumer advances the incident to
`VERIFYING` for 60 s → `RESOLVED` (healthy) or `FAILED` (re-breach). Verified on
incident `1cabf404…`: remediated 09:15:31 → `resolvedAt 09:16:31`
(60 s window) → **`RESOLVED`, `remediationOutcome=COMPLETED`**. Exercise
`RESOLVED→FAILED` by keeping the generator running during the window (re-breach)
or `docker pause vykronis-agent-orchestrator-1` during the window to guarantee a
clean `RESOLVED`.

## Step 14 — The learn table  `[x]`

```bash
docker compose -f infra/compose/docker-compose.yml exec postgres psql \
  -U vykronis -d vykronis -c \
  "select incident_id, service_id, environment, action, result, window_start, window_end \
   from remediation_learn order by verified_at desc limit 3"
```

Verified row: `1cabf404… | payment-service | PROD | ROLLBACK | VERIFIED |
2026-09-18 09:15:31 → 09:16:31`. Note the column is `environment`, not `env`.

## Step 15 — Bonus knobs (other data kinds Vykronis acts on)  `[x]`

```bash
curl -X POST http://localhost:8081/api/ingest/traces -d '{...}'   # traces
curl -X POST http://localhost:8081/api/ingest/jfr   -d '{...}'   # JFR records
curl http://localhost:8090/contracts                             # schema-registry (5 topics)
curl http://localhost:8090/contracts/obs.metrics/validate -X POST -d '{"..."}'
```

---

## Session notes / gotchas

- **Orphan containers warning** on compose `up` is expected after prior runs — the
  app containers from an earlier session are reused by the apps overlay in Step 2.
- The old **kind cluster** node was deleted mid-session (was consuming ~40% CPU).
- The host has 8 GB RAM; the VM is capped at **6 GB** (see below). Keep VS Code /
  browser windows closed for the demo.
- **Compose fixes applied 2026-09-15/17** (all in `infra/compose`):
  - `docker-compose.apps.yml`: added the `common-platform` env anchor so services
    reach Postgres/Kafka by compose service name (`postgres`, `kafka`) instead of
    `localhost` (which resolves to the container itself) — added
    `VYKRONIS_DB_URL`, `VYKRONIS_DB_USER/PASSWORD`, `VYKRONIS_KAFKA_BOOTSTRAP`.
  - `JAVA_TOOL_OPTIONS: -Xmx256m` on every app service — 9 JVMs otherwise auto-size
    to ~1 GB each and swap-thrash the whole VM (kafka flips unhealthy, DB connects
    time out).
  - `docker-compose.yml`: kafka `KAFKA_ADVERTISED_LISTENERS` changed from
    `PLAINTEXT://localhost:9092` to `PLAINTEXT://kafka:9092` — clients *inside* other
    containers must be told the compose-service name or Kafka Streams consumers
    silently fail to connect ("no resolvable bootstrap" / global-store timeout).
  - kafka healthcheck timeout raised 5 s -> 30 s (the `kafka-topics.sh --list` probe
    takes ~6 s on this slow box, so the check kept false-`unhealthy`).
  - **stale images**: `correlation-engine:local` predated a Sept 13 fix. Rebuilt
    on the host (`mvn package` + `Dockerfile.hostbuild`, jar staged in
    `build-staging/`) rather than inside the VM. If a service image is stale, use
    the same pattern.
  - **memory**: `.wslconfig` `memory=6GB` (whole number — `5.5GB` is silently
    ignored) + Docker Desktop `MemoryMiB: 6144`, so 9 JVMs + Kafka fit without
    thrash. Host keeps ~2 GB free.
  - **two Kafka env spellings**: agent-orchestrator's `DemoKafkaConfig` reads
    `spring.kafka.bootstrap-servers`, NOT the platform's `VYKRONIS_KAFKA_BOOTSTRAP`
    — without it, the deploy endpoint returns 500 while the producer re-bootstraps
    to `localhost:9092` (itself). `common-otel` now sets BOTH
    `VYKRONIS_KAFKA_BOOTSTRAP: kafka:9092` and
    `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092`.
- **correlation-engine self-bootstrap**: `TopicBootstrap` (new class) creates
  `obs.metrics`, `obs.deployments`, `obs.alerts` with an AdminClient in
  `@PostConstruct` (before the streams `SmartLifecycle`). Without it the global
  store on `obs.deployments` fails at boot with "no partitions available" whenever
  that topic is missing (coiled with the demo's auto-create-on-first-write).
  `depends_on: kafka (service_healthy)` also gates correlation at compose level,
  and `TopicBootstrap` retries `listTopics` (30 s x5 with 5 s backoff) so a
  freshly-recreated broker mid-boot no longer crashes the app (seen 2026-09-17:
  `TimeoutException` on the very first cold Kafka start).

- **2026-09-17/18 session findings**:
  - **incident-service needs its own cross-service URLs** (same `localhost` trap):
    `VYKRONIS_ORCHESTRATOR_URL=http://agent-orchestrator:8085` and
    `VYKRONIS_POLICY_URL=http://policy-service:8086`. Missing the policy URL made
    remediation 503. event-service needs `VYKRONIS_OPENSEARCH_URL=http://opensearch:9200`.
  - **`GET /api/incidents` returns a plain JSON array** (not `{"value":…}`) — parse
    with PowerShell `(Invoke-RestMethod …) | ConvertFrom-Json`.
  - **Event search is OpenSearch-only**: `/api/search/events` 500s when OpenSearch
    (`--profile search up -d opensearch`) is down; the `obs-events` index is created
    at event-service startup, so restart event-service after OpenSearch comes up.
  - **Two OOM episodes (2026-09-17 & 18)**: the whole VM died (all `vykronis-*`
    containers `Exited (255)`) with host free memory at ~0.3-0.6 GB. Recovery:
    leave OpenSearch off (saves ~1 GB), run
    `docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile apps up -d`
    (both `-f` files matter — app services show up as "orphans" otherwise), then
    `start correlation-engine` once kafka reports `healthy`.
  - **RAM (2026-09-18)**: VM cap raised 6 GB → 7 GB (`.wslconfig` memory=7GB,
    Docker `settings-store.json` MemoryMiB=7168). Still tight: keep api-gateway /
    ingestion-service / schema-registry **stopped** (they are not needed for the
    core loop), and expect ~0.4-0.6 GB host free while demoing.
  - **remediation result/command bus**: `obs.remediation` carries *both* command
    and result records. The two consumers each ignore the other type via tolerant
    deserializers (`RemediationCommandDeserializer`, `RemediationResultDeserializer`;
    they return null for foreign records, which the listeners skip).
  - **`@EnableKafka`**: remediation-service was missing it (incident-service has it),
    so its `@KafkaListener` was never registered — the consumer never joined the
    group (group `remediation-service` sat "Empty", incident stayed `REMEDIATING`).
    Fixed 2026-09-18; the live group is `remediation-service2`
    (`VYKRONIS_REMEDIATION_GROUP_ID`) because the crashed group's coordinator
    partition is unrecoverable (only that group hangs on `FIND_COORDINATOR`;
    `kafka-consumer-groups --delete` needs the same coordinator and also times out).
  - **incident-service image**: switched to copy-the-prebuilt-jar build
    (`platform/incident-service/Dockerfile.hostbuild` + `build-staging/incident-app.jar`)
    — the in-container `mvn package` previously stalled for 10+ min under memory
    pressure.

---
## GUARD (added 2026-09-18) — volumes must never be removed

- **Never run** \docker compose down --volumes\ (or \-v\) on this project.
  \down --volumes\ deletes the three named data volumes — \pgdata\,
  \kafka-data\, \edis-data\ — which hold the incident DB, Kafka topics,
  and demo learn-table state. Data is re-seeded on next \up\, but the
  accumulated learn rows + topic history are gone.
- \up -d --build\ (the only bring-up used by this walkthrough) **never**
  touches volumes — it creates-then-reuses them. Safe to run repeatedly.
- Bare \down\ (no flag) also keeps volumes. Only \down\ + \--volumes\
  destroys them.

