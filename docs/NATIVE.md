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
| event-service | `vykronis/event-service:native` | yes | 21.974s (with Postgres) |
| agent-orchestrator | `vykronis/agent-orchestrator:native` | yes | 8.107s |

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

First matrix dispatch (`34687630947`, sha `a72c794`): both jobs **success**;
binaries live on GHCR as `ghcr.io/sreelekha-22/vykronis/<svc>:native`.

| service | image size (GHCR) | boot time (16GB runner) | memory (approx) |
|---|---|---|---|
| policy-service | 153.4 MB (146.3 MiB) | **2.182 s** | 257.8 MiB* |
| api-gateway | 159.9 MB (152.5 MiB) | **3.536 s** | 310.4 MiB* |

Size = uncompressed layer total from `docker manifest inspect` (the pushed
image; `docker image inspect .Size` locally = same number). The job summary
reported "358M"/"367M" — that's the build machine's own `{{.Size}}` quirk, not
the published artifact. Boot = CI-runner log line (`Started … in N seconds`) —
the real win vs typical JVM cold start. *= cgroup usage via `docker stats`
measured locally the same day; that run's "container RSS (approx)" came out as
`runtime kB` because the a72c794 script used `docker exec … sh` and the
buildpack image is shell-less — fixed in `6e0431a` (falls back to
`docker stats`), so the next dispatch will report runner-side memory cleanly.

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