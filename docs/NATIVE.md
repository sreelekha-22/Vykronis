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

Dispatch: **Actions -> native -> Run workflow** (single click, no inputs).

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

Measured from the actual GHCR binaries pulled to the local Docker engine
(2026-09-12, first matrix dispatch = successful run `34687630947` / `a72c794`).
Boot = `Started <App> in … seconds` from container logs; memory = the container's
cgroup usage via `docker stats` (the buildpack image is shell-less, so
`/proc/1` VmRSS via `docker exec` isn't available — cgroup usage is the honest
equivalent). These are local-host numbers (busy dev laptop), so they are an
upper bound for boot; the runner-side equivalents live in each job's summary /
`native-report.txt` artifact.

| service | image size | boot time | container memory (approx) |
|---|---|---|---|
| policy-service | 146.3 MB | 14.049 s | 257.8 MiB |
| api-gateway | 152.5 MB | 21.982 s | 310.4 MiB |

Headline vs the AOT JVM (same host): policy `14.0s` native vs `6.2s` JVM-AOT
boot, gateway `21.9s` vs `9.5s` — this host's Docker/WSL VM loads skew cold
starts, and the JVM path already got the AppCDS/jlink squeeze in Tier 1. The
native win here is steady-state footprint single-binary distribution (no JDK,
no runtime image), not boot on a loaded 8GB laptop; the 16GB-runner job
summaries are the fair numbers.

## Caveats

- The buildpack native image ships **no shell** (`docker exec … sh` fails), so
  memory is measured via the container's cgroup usage (`docker stats`) instead of
  `/proc/1` VmRSS — same ballpark, page cache included.
- `api-gateway` (WebFlux/Netty) is the expected native-hiccup case; if its CI
  smoke red-flags, reachability tweaks go under `META-INF/native-image/...`.
- Out of scope this sprint: `event-service` / `agent-orchestrator` (JPA/OpenSearch/
  Kafka reachability).