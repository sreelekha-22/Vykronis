# Phase 6 Demo — Remediate + verify (Compose)

## Overview
This demo closes the autonomous loop end-to-end on Docker Compose:
a fault in a service is detected, an incident is correlated, a
remediation is proposed by the agent, a policy decides whether
human approval is required, the remediation service executes the
rollback (or restart) against Compose, the incident enters a
verification window, and the outcome is recorded in the learn
table. The UI timeline walks every state from `OPEN` through
`RESOLVED` (or `FAILED`).

## Prerequisites
- Java 25
- Docker + Docker Compose (the **host's** `docker` CLI is used by
  the remediation service; the service must be able to talk to the
  host daemon)
- `vykronis` Maven wrapper (or `mvn`)
- A running `vykronis` Postgres / Kafka / Redis (the base compose
  stack from Phase 1)
- `curl`, `jq`

## Architecture (Phase 6 addition)
```
DemoRunner → Kafka(obs.metrics) → EventService → Postgres
                              ↘ CorrelationEngine → IncidentService
                                                 ↘ AgentOrchestrator (hypothesis)
                                                 ↘ PolicyService (ALLOW | REQUIRE_APPROVAL | DENY)
                                                 ↘ Kafka(obs.remediation) → RemediationService
                                                                          ↘ ComposeExecutor
                                                                              ↘ docker compose (host socket)
                                                 ← Kafka(obs.remediation)  RemediationResult
                                                 ↘ VERIFYING → (sweeper / re-breach) → RESOLVED | FAILED
                                                                              ↘ remediation_learn (VERIFIED | NOT_VERIFIED)
                              → API Gateway → UI (timeline through RESOLVED)
```

## Ports (Phase 6)
| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Ingestion | 8081 |
| Event | 8082 |
| Correlation | 8083 |
| Incident | 8084 |
| Demo | 8085 |
| Policy | 8086 |
| Remediation | 8087 |

---

## The 15 steps

### 1. Start the base infrastructure
```bash
cd Ob_Autonomous_Platform
docker compose -f infra/compose/docker-compose.yml up -d
```
Wait for `kafka`, `postgres`, and `redis` to report `healthy`.

### 2. Add the remediation service to the app overlay
Append a `remediation-service` block to `infra/compose/docker-compose.apps.yml`
(or run it locally on port 8087 — both work). The container must
mount the **host** docker socket and ship the docker CLI, because
the executor shells out to `docker compose`:

```yaml
  remediation-service:
    image: eclipse-temurin:25-jre
    container_name: vykronis-remediation
    working_dir: /app
    volumes:
      - ./:/app
      - /var/run/docker.sock:/var/run/docker.sock
      - /usr/bin/docker:/usr/bin/docker:ro
    command: ["java","-jar","platform/remediation-service/target/remediation-service-0.1.0-SNAPSHOT.jar"]
    environment:
      VYKRONIS_DB_URL: "jdbc:postgresql://postgres:5432/vykronis"
      VYKRONIS_DB_USERNAME: vykronis
      VYKRONIS_DB_PASSWORD: vykronis
      VYKRONIS_KAFKA_BOOTSTRAP: "kafka:9092"
      REMEDIATION_COMPOSE_FILES: "infra/compose/docker-compose.yml,infra/compose/docker-compose.apps.yml"
    ports: ["8087:8087"]
    depends_on:
      postgres: { condition: service_healthy }
      kafka:    { condition: service_healthy }
```

The socket mount is what lets the executor run `docker compose up -d
--no-deps <service>` against the host's compose files.

### 3. Build everything
```bash
./mvnw clean install
```

### 4. Bring up the app overlay (with the remediation block)
```bash
docker compose -f infra/compose/docker-compose.yml \
               -f infra/compose/docker-compose.apps.yml up -d
```

### 5. Health check
```bash
curl -fsS http://localhost:8080/actuator/health | jq .status
curl -fsS http://localhost:8084/actuator/health | jq .status
curl -fsS http://localhost:8087/actuator/health | jq .status
```
All three should report `UP`.

### 6. Start the fault scenario
```bash
mvn -pl platform/agent-orchestrator spring-boot:run -Dspring-boot.run.profiles=demo-traffic
```
`DemoRunner` produces a sustained `error_rate` / `error_count`
spike to topic `obs.metrics`. (Equivalent: a manual
`POST /api/ingest/events` with `type=METRIC`, `error_rate=25`.)

### 7. Watch the metric events
```bash
docker compose -f infra/compose/docker-compose.yml exec kafka \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic obs.metrics --from-beginning --max-messages 5 | jq .
```

### 8. Confirm event-service persisted them
```bash
curl "http://localhost:8082/api/events?serviceId=payment-service&type=METRIC&limit=5" | jq '.items | length'
```

### 9. Watch the correlation-engine coalesce them
```bash
curl "http://localhost:8083/api/correlation/candidates?serviceId=payment-service" | jq .
```
A single candidate should remain.

### 10. Incident appears — `OPEN`
```bash
curl http://localhost:8084/api/incidents | jq '.[0] | {incidentId,status,serviceId,severity}'
```
Status is `OPEN`. Open the UI (`http://localhost:8080/incidents`) —
the timeline renders `Status: OPEN` in the header.

### 11. Investigate — the agent forms a hypothesis
```bash
curl -X POST http://localhost:8084/api/incidents/<id>/investigate | jq .
```
The `Hypothesis` (source: `ai` or `fallback`) is attached.
Status moves to `HYPOTHESIS_READY`. The UI shows the hypothesis
in the Investigate panel.

### 12. Request remediation — the policy decides
```bash
curl -X POST http://localhost:8084/api/incidents/<id>/remediation \
  -H "Content-Type: application/json" \
  -d '{"subject":{"name":"ops","roles":["approver"]}}' | jq '.status,.policyDecision'
```
On `PROD` + `critical` the matrix returns `REQUIRE_APPROVAL`; the
incident advances to `AWAITING_APPROVAL`. A `DEV` incident would
auto-approve (`ALLOW`) and skip straight to step 13. A `DENY` (e.g.
unknown service) skips to `FAILED` with `policyDecision=DENY`.

### 13. Approve — incident goes `REMEDIATING`, executor runs Compose
```bash
curl -X POST http://localhost:8084/api/incidents/<id>/approve \
  -H "Content-Type: application/json" \
  -d '{"subject":{"name":"ops","roles":["approver"]}}' | jq '.status'
```
The incident emits a `RemediationCommand` to topic
`obs.remediation` (key = `commandId`). The remediation service
consumes it; `ComposeExecutor` first checks the idempotency store
by `commandId`, then runs:

```
docker compose -f infra/compose/docker-compose.yml \
               -f infra/compose/docker-compose.apps.yml \
               up -d --no-deps agent-orchestrator
```

A `RESTART` action runs `... restart agent-orchestrator` instead. On
exit `0` the executor stores a `COMPLETED` result; otherwise
`FAILED` (the docker stderr is trimmed into the learn `detail`).
A duplicate `commandId` replay is a no-op — the stored result is
returned, the runner is **not** invoked again (Unit 6).

### 14. Result lands — incident enters the verification window
```bash
curl http://localhost:8084/api/incidents/<id> | jq '.status,.remediationOutcome'
```
Status moves to `VERIFYING`, `remediationOutcome=COMPLETED`,
`verifyDeadline = now + 60s` (env `VYKRONIS_VERIFY_WINDOW_SECONDS`).

A scheduled sweeper (`VerificationSweeper`,
`vykronis.verify-sweep-ms` = 10s) and the live alert consumer
race for the verdict:

- **Healthy window** — when the deadline passes without a
  re-breach, the sweeper transitions the incident to `RESOLVED`
  and writes a `remediation_learn` row with `result=VERIFIED`
  (windowStart = `remediationCompletedAt`, windowEnd = deadline).
- **Re-breach** — a new `obs.alerts` candidate for the same
  service/env while the incident is still `VERIFYING` calls
  `VerificationService.onReBreach(...)` which transitions the
  incident to `FAILED` and writes a learn row with
  `result=NOT_VERIFIED`.
- **Executor failed** — a `FAILED` result transitions directly to
  `FAILED` (no verification window).

### 15. Inspect the timeline and the learn table
UI (`http://localhost:8080/incidents/<id>`) — the header reads
`Status: RESOLVED` (or `FAILED`); the remediation panel reports
`Resolved — remediation verified (COMPLETED)`.

API:
```bash
curl http://localhost:8084/api/incidents/<id> | jq '{status,remediationOutcome,resolvedAt}'
```
The remediation service also has the run record (keyed by
`commandId`):
```bash
curl http://localhost:8087/api/remediation/<commandId> | jq .
```

Postgres — the learn table:
```bash
docker compose -f infra/compose/docker-compose.yml exec postgres \
  psql -U vykronis -d vykronis -c \
  "select incident_id, service_id, env, action, result, window_start, window_end, verified_at from remediation_learn order by verified_at desc limit 5;"
```

That's the closed loop: a fault (an error burst on `payment-service`, seeded by
the demo generator) is detected, correlated, hypothesised, policy-gated, rolled
back, verified, and recorded. The next time the same pattern emerges the learn
row makes the agent's suggestion more confident.
