# GraalVM Native (Tier 2)

> **Status (2026-09-12):** real native binaries are built and measured. Built
> via `spring-boot:build-image -P native` (Spring AOT engaged through the
> `native` profile's `<extensions>true</extensions>`); all four native images are
> on GHCR as `ghcr.io/sreelekha-22/vykronis/<svc>:native` and each service's
> Gradle -free smoke reports `/actuator/health` UP. The 2026-09-12 "JVM not
> native" correction is resolved — the `Measured numbers` section below is now
> the real native build (dispatch `34709798539` / `32ea577`).

## What

Each service can be compiled into a self-contained native binary via Spring AOT +
GraalVM `native-image`. Goal: ~40-60MB image, ~100ms boot, ~60MB RSS — the
"weights-less" runtime credential for the demo.

## Why it builds in CI, not locally

`native-image` compilation needs ~6-8GB of RAM. The dev laptop has 8GB physical
(and the Docker/WSL VM is capped at 6GB), so the binary build is delegated to
GitHub's 16GB `ubuntu-latest` runners. Local work still adds value: AOT processing
and hint generation are cheap and run on the host JVM.

## Build

Dispatch: **Actions -> native -> Run workflow** (single click, no inputs).

Matrix (grows over time):

| service | image | native profile | AOT JVM boot |
|---|---|---|---|
| policy-service | `vykronis/policy-service:native` | yes | 6.224s |
| api-gateway | `vykronis/api-gateway:native` | yes | 9.484s |
| event-service | `vykronis/event-service:native` | yes | 21.974s (with Postgres) |
| agent-orchestrator | `vykronis/agent-orchestrator:native` | yes | 8.107s |

Per service the job:

1. `./mvnw -pl platform/<svc> -am install -DskipTests` (jar + reactor deps)
2. `./mvnw -P native -pl platform/<svc> spring-boot:build-image -Dspring-boot.build-image.nativeImage=true` (Boot buildpack, AOT jar)
3. pushes `ghcr.io/sreelekha-22/vykronis/<svc>:native` (public packages are free)
4. `docker run` -> asserts `/actuator/health` UP -> measures image size, boot time, RSS
5. uploads `native-report.txt` and writes the metrics table to the job summary

## Local verification without `native-image`

```bash
./mvnw -pl platform/<svc> -Pnative spring-boot:process-aot          # generate hints
./mvnw -pl platform/<svc> spring-boot:run -Dspring-boot.run.aot=true -Dspring-boot.run.arguments=--server.port=0
```

The second command boots the AOT-built context on the JVM — a cheap completeness
check of the generated `reachability-metadata.json` before spending CI minutes.

## Measured numbers

Real native binaries; smoke on 16GB `ubuntu-latest` (dispatch `34709798539`,
commit `32ea577`). image size = `docker image inspect .Size` `numfmt --to=iec`;
boot = `Started … in N seconds`; RSS = `docker stats` container memory.

| service | image size | boot time | RSS (approx) | native-image build peak |
|---|---|---|---|---|
| policy-service | **177 MB** | **0.088 s** | **~55 MiB** | 5.55 GB / ~4 min |
| api-gateway | **202 MB** | **0.163 s** | **~73 MiB** | 5.74 GB / ~4 min |
| event-service | **300 MB** | **1.258 s** | **~103 MiB** | 11.12 GB / ~6 min |
| agent-orchestrator | **310 MB** | **0.172 s** | **~88 MiB** | 10.92 GB / ~8 min |

All four smoke UP. event-service smoke boots an ephemeral Postgres container
(needed by design); event/agent disable the Kafka health contributor so a
missing broker doesn't flip health DOWN.

## Full-stack demo in kind (native cores)

**Actions -> native-kind-smoke -> Run workflow** (single click, no inputs)
wraps the four native cores into the real demo stack instead of smoke containers:

1. pulls the published `ghcr.io/sreelekha-22/vykronis/<svc>:native` images
   (anonymous, public) and re-tags them `vykronis/<svc>:local`
2. builds the four remaining JVM support services (ingestion, correlation,
   incident, remediation) from `infra/compose/docker-compose.apps.yml`
3. loads all eight into a fresh `vykronis-smoke` kind cluster, installs the
   helm chart, and waits for every pod `Ready` (`-Dhelm.smoke.kind=true`)

Because the native binaries compile in the same `server.port` values as their
JVM twins, the chart's probes (8080/8082/8085/8086) and ClusterIP services map
1:1. Kafka + Postgres are real Deployments/StatefulSets, so the native
event/agent Kafka health contributors come up naturally (no DISABLE envs).
Requires the `native` workflow to have been run at least once so the images
exist on GHCR. On failure the cluster is preserved and kind logs are uploaded.

## App-level reachability metadata

Spring Boot's AOT toolchain **overwrites** `META-INF/native-image/<groupId>/<artifactId>/reachability-metadata.json`
with its own generated file, silently dropping hand-authored hints at the same
coords. App-level hints therefore live under `META-INF/native-image/io.vykronis/<service>-native-hints/reachability-metadata.json`
(non-clobbered coords, applied by native-image discovery) and use the object
schema this GraalVM accepts — class descriptors keyed by `"type"`, resources
by `"glob"`:

- hibernate-validator typed loggers (`Log_$logger`, `Messages_$bundle`) — all
  native services
- `com.google.protobuf.ExtensionRegistry[Lite]` — reflective `getEmptyRegistry`
  in the OTLP exporter path
- `org.hibernate.dialect.PostgreSQLDialect` — event-service (reflective dialect
  instantiation)
- `schema/*.json` glob — agent-orchestrator (JSON Schema validation resource)

## Caveats

- The buildpack native image ships **no shell** (`docker exec … sh` fails), so
  memory is measured via the container's cgroup usage (`docker stats`) instead of
  `/proc/1` VmRSS — same ballpark, page cache included.
- `api-gateway` (WebFlux/Netty) is the expected native-hiccup case; if its CI
  smoke red-flags, reachability tweaks go under `META-INF/native-image/...`.
- Out of scope this sprint (native still untested): none — policy, gateway, event,
  orchestrator are all in the matrix. `event-service`'s smoke boots an ephemeral
  Postgres container (it needs a DB by design) + disables the Kafka health
  contributor; `agent-orchestrator`/`event-service` both disable the Kafka health
  contributor so a missing broker doesn't flip health to DOWN.