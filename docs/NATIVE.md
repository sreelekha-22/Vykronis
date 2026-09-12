# GraalVM Native (Tier 2)

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

Opt-in dispatch: **Actions -> CI -> Run workflow -> `run-native=true`**.

Matrix (grows over time):

| service | image | native profile | AOT JVM boot |
|---|---|---|---|
| policy-service | `vykronis/policy-service:native` | yes | 6.224s |
| api-gateway | `vykronis/api-gateway:native` | yes | 9.484s |

Per service the job:

1. `./mvnw -pl platform/<svc> -am install -DskipTests` (jar + reactor deps)
2. `spring-boot:build-image -Dspring-boot.build-image.nativeImage=true` (Boot buildpack)
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

Filled in from `native-report.txt` after the first matrix dispatch.

| service | image size | boot time | container RSS (approx) |
|---|---|---|---|
| policy-service | TBD | TBD | TBD |
| api-gateway | TBD | TBD | TBD |

## Caveats

- Container RSS from `/proc/1/status` VmRSS is approximate (page cache included).
- `api-gateway` (WebFlux/Netty) is the expected native-hiccup case; if its CI
  smoke red-flags, reachability tweaks go under `META-INF/native-image/...`.
- Out of scope this sprint: `event-service` / `agent-orchestrator` (JPA/OpenSearch/
  Kafka reachability).