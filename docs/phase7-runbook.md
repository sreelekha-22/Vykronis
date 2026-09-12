# Phase 7 Runbook — kind/k3d + Helm install (15-step demo on Kubernetes)

## Overview
This runbook shows how to run the **same 15-step north-star demo** from Phase 6 on a local Kubernetes cluster (kind or k3d) instead of Docker Compose. The demo flow is identical: fault → incident → policy-gated approve → remediation (`kubectl rollout undo/restart`) → verification window → learn table. The only substrate change is the executor target.

> **Honest load claim:** this platform is not a 100k req/sec throughput engine. It is a reliability loop — ingest, correlate, hypothesise, remediate, verify — that runs at *human* incident cadence (seconds to minutes per loop). The load numbers below reflect the demo scenario, not a stress benchmark.

## Prerequisites
- Docker (for kind/k3d container runtime)
- `kubectl` ≥ 1.29
- `helm` ≥ 3.14
- `kind` ≥ 0.24 **or** `k3d` ≥ 5.6
- Java 25 + Maven wrapper (for building images if you rebuild)

> **Windows note:** kind runs Linux containers; you need WSL 2 or Docker Desktop with Kubernetes disabled (kind manages its own cluster). k3d works natively on Windows via Docker.

## Quickstart (kind)

### 1. Create a kind cluster
```bash
kind create cluster --name vykronis --config - <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "vykronis.io/node-type=control-plane"
  - role: worker
    labels:
      vykronis.io/node-type: worker
  - role: worker
    labels:
      vykronis.io/node-type: worker
EOF
```

### 2. Load local images (if you rebuilt)
```bash
# Only needed if you changed service code and want your images
kind load docker-image vykronis/ingestion-service:local --name vykronis
kind load docker-image vykronis/event-service:local --name vykronis
# ... repeat for all 9 platform services
```

### 3. Add the Helm repo (or use local chart)
```bash
# If you have a chart repo:
helm repo add vykronis https://charts.vykronis.io
helm repo update

# OR install from the local chart (repo root):
cd Ob_Autonomous_Platform
helm install vykronis ./infra/helm/vykronis -n vykronis --create-namespace
```

### 4. Wait for pods
```bash
kubectl -n vykronis wait --for=condition=Ready pods --all --timeout=300s
```

### 5. Verify health
```bash
kubectl -n vykronis get pods -o wide
# All should be Running with 1/1 ready
```

## Quickstart (k3d)

### 1. Create a k3d cluster
```bash
k3d cluster create vykronis \
  --agents 2 \
  --port "8080:80@loadbalancer" \
  --port "9092:9092@loadbalancer" \
  --k3s-arg "--disable=traefik@server:0" \
  --wait
```

### 2. Install the chart
```bash
cd Ob_Autonomous_Platform
helm install vykronis ./infra/helm/vykronis -n vykronis --create-namespace
```

### 3. Wait + verify
```bash
kubectl -n vykronis wait --for=condition=Ready pods --all --timeout=300s
kubectl -n vykronis get pods
```

## The 15 Steps (identical to Phase 6 Compose demo)

| # | Step | Compose | kind/k3d |
|---|------|---------|----------|
| 1 | Start infra | `docker compose up -d` | `kind create cluster` / `k3d cluster create` |
| 2 | Deploy app | `docker compose -f ...apps.yml up -d` | `helm install vykronis ./infra/helm/vykronis` |
| 3 | Health check | `curl /actuator/health` | `kubectl exec -it deploy/api-gateway -- curl localhost:8080/actuator/health` |
| 4 | Start fault | `mvn -pl agent-orchestrator spring-boot:run -Dprofiles=demo-traffic` | Same (runs locally against cluster Kafka) |
| 5 | Watch metrics | `kafka-console-consumer --topic obs.metrics` | Same (Kafka is in-cluster; port-forward 9092) |
| 6 | Event persist | `curl /api/events` | Same (port-forward 8082) |
| 7 | Correlation | `curl /api/correlation/candidates` | Same (port-forward 8083) |
| 8 | Incident OPEN | `curl /api/incidents` | Same (port-forward 8084) |
| 9 | UI incident list | `http://localhost:8080/incidents` | `kubectl port-forward svc/api-gateway 8080:80` then open |
| 10 | Investigate | `POST /investigate` | Same |
| 11 | Request remediation | `POST /remediation` | Same |
| 12 | Approve | `POST /approve` | Same |
| 13 | Executor runs | `docker compose up -d --no-deps agent-orchestrator` | `kubectl rollout undo deployment/agent-orchestrator -n vykronis` |
| 14 | Verify window | `VERIFYING` 60s → `RESOLVED` / re-breach → `FAILED` | Identical (incident-service is the same) |
| 15 | Learn table | `psql remediation_learn` | `kubectl exec -it deploy/postgres -- psql -U vykronis -d vykronis -c "select ... from remediation_learn"` |

### Key differences on k8s

1. **Remediation target** — the Helm chart sets `vykronis.remediation.target=kubernetes` (values.yaml). The `RemediationCommandConsumer` wires the **KubernetesExecutor** (Phase 7 Unit 2). Same idempotency, same Kafka contract.
2. **Executor command** — `ROLLBACK` → `kubectl rollout undo deployment/<svc> -n vykronis`; `RESTART` → `kubectl rollout restart deployment/<svc> -n vykronis`. No docker socket needed.
3. **Service exposure** — use `kubectl port-forward` for all API endpoints (or an Ingress if you add one). The demo ports table from Phase 6 applies but behind the port-forwards.
4. **Postgres** — deployed by the chart as a StatefulSet with PVC; `kubectl exec` into it for the learn-table query.
5. **Kafka** — Strimzi operator (or embedded KRaft via chart) exposes `:9092` inside the cluster; port-forward `9092` to your host for the `kafka-console-consumer` step.

## Port-forwards cheat sheet
```bash
# Run each in its own terminal
kubectl -n vykronis port-forward svc/api-gateway       8080:80     # Gateway / UI
kubectl -n vykronis port-forward svc/ingestion-service 8081:8081   # Ingest
kubectl -n vykronis port-forward svc/event-service     8082:8082   # Events
kubectl -n vykronis port-forward svc/correlation       8083:8083   # Correlation
kubectl -n vykronis port-forward svc/incident-service  8084:8084   # Incidents
kubectl -n vykronis port-forward svc/kafka             9092:9092   # Kafka
```

## Observed demo timing (honest numbers)

| Phase | Typical wall time (kind, 2 agents, laptop) |
|-------|--------------------------------------------|
| Cluster up + Helm install | 90–150s |
| Pods ready | 30–60s post-install |
| Fault → Incident OPEN | 5–15s (correlation window) |
| Investigate → Approve | <1s (AI/fallback + policy table) |
| Executor (rollback) | 10–30s (k8s rollout undo + pod ready) |
| Verify window | 60s fixed (`vykronis.verify-window.seconds`) |
| End-to-end | ~2–3 minutes |

> **Not a throughput benchmark.** These are single-incident latencies on a 2-agent kind cluster. Scaling the correlation engine / ingestion / event-service horizontally is future work (Phase 7 stretch).

## Cleanup
```bash
# kind
kind delete cluster --name vykronis

# k3d
k3d cluster delete vykronis
```

## Helm values reference (what the demo flips)

```yaml
# infra/helm/vykronis/values.yaml (excerpt)
vykronis:
  remediation:
    target: kubernetes            # ComposeExecutor vs KubernetesExecutor
    kubernetes:
      namespace: vykronis
      kubectlBinary: kubectl

  verify:
    windowSeconds: 60
    sweepMs: 10000

  auth:
    enabled: false                # Mode A: anonymous demo operator
```

Flip `auth.enabled=true` and deploy Keycloak (see Phase 5 docs) to run the secured demo path.

## CI integration
The GitHub Actions workflow (Phase 7 Unit 3) runs the full reactor on `ubuntu-latest` + `windows-latest` with the JaCoCo gate (line ≥ 80%, branch ≥ 70%). It also ships a standalone **`kind-smoke`** workflow: run it from **Actions → kind-smoke → Run workflow** (single click, no inputs). The job installs helm/kind/kubeconform/kubectl on `ubuntu-latest`, builds `vykronis/runtime:local` + the 8 `vykronis/<service>:local` images via compose, installs the chart on a fresh kind cluster, and asserts all pods reach `Ready` (equivalent to the manual demo below). On failure it uploads the kind node logs.

A GraalVM **`native`** workflow (Tier 2) is standalone: **Actions → native → Run workflow** (single click, no inputs) builds native binaries via the Spring Boot buildpack (16 GB runner; the 8 GB host laptop can't run `native-image`) for the **matrix** `policy-service`, `api-gateway`, `event-service`, and `agent-orchestrator` into `vykronis/<svc>:native`, pushes them to GHCR (`ghcr.io/sreelekha-22/vykronis/<svc>:native`), runs each, and asserts `/actuator/health` is `UP` — also reporting image size, boot time, and an approximate container RSS into the job summary and a `native-report.txt` artifact. All four services carry the `native` Maven profile; AOT hint generation is verified locally via `./mvnw -pl platform/<svc> -Pnative spring-boot:process-aot` + an AOT JVM boot. Measured numbers (policy 177 MB/0.088 s/~55 MiB, api-gateway 202 MB/0.163 s/~73 MiB, event-service 300 MB/1.258 s/~103 MiB, agent-orchestrator 310 MB/0.172 s/~88 MiB) are in **`docs/NATIVE.md`**.

## What's not in this runbook (stretch only)
- Terraform for kind cluster provisioning
- k6 load script (the platform is not a load generator)
- Pod kill / chaos injection (the remediation loop *is* the self-healing demo)
- Schema Registry (contracts use JSON, not Avro/Proto)

---

*This runbook is the Phase 7 Unit 4 deliverable. It documents the exact 15-step demo on Kubernetes without inflating load claims. When you can install `helm` + `kind`/`k3d`, follow it to reproduce the Compose demo on a cluster.*