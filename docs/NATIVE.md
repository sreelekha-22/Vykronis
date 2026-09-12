# GraalVM Native (Tier 2)

> **Correction (2026-09-12):** the GHCR images currently tagged `:native`
> are **not** native binaries — they are ordinary JVM images. `docker image
> inspect` build metadata shows BellSoft Liberica JRE + `java ... JarLauncher`
> as the `web` process and no `native-image` buildpack. Root cause: the build
> step ran `spring-boot:build-image -Dspring-boot.build-image.nativeImage=true`
> **without activating the `native` profile**, so Spring AOT (`process-aot`)
> never ran, the buildpack had no AOT classes and silently built a JVM image
> (the distroless-tiny run base's lack of a shell was a red herring that
> masked this). Fixed (2026-09-12): the native profile now sets
> `<extensions>true</extensions>` on `native-maven-plugin` (hooks AOT into
> `package`) and the workflow passes `-P native`; verified locally that
> `package` now embeds `*__ApplicationContextInitializer` / `*__BeanFactoryRegistrations`
> AOT classes. The metrics table below is therefore **JVM** performance until a
> corrected dispatch produces and measures real native binaries.

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

Four-service native matrix; binaries on GHCR as
`ghcr.io/sreelekha-22/vykronis/<svc>:native`.

| service | image size (GHCR) | boot time (16GB runner) | memory (approx) |
|---|---|---|---|
| policy-service | 153.4 MB (146.3 MiB) | **2.862 s** | 257.8 MiB* |
| api-gateway | 159.9 MB (152.5 MiB) | **3.495 s** | 310.4 MiB* |
| agent-orchestrator | 216.0 MB (206.0 MiB) | **3.505 s** | 340.2 MiB* |
| event-service | 188.2 MB (179.5 MiB) | **5.740 s** | 355.8 MiB* |

Boot = the `Started … in N seconds` line from each job log (all four from the
4-matrix dispatch `34693808237` / `06fc143`). Memory = cgroup usage via
`docker stats` on the same GHCR binaries locally.

Size = uncompressed layer total from `docker manifest inspect` (matches
`docker image inspect .Size` locally). The job-summary image sizes (e.g.
"358M"/"395M") are the build machine's own `{{.Size}}` quirk, not the pushed
artifact. The job-summary "container RSS (approx)" printed `runtime kB` on the
shell-less buildpack image (`docker exec … sh` fails) — the workflow now drops
the exec attempt and always takes `docker stats`, so future dispatches report
runner-side memory cleanly. *= memory from `docker stats` (cgroup usage) on the
same binaries.

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