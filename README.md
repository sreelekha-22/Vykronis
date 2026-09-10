# Vykronis — Autonomous Reliability & Remediation Platform

**Loop:** Observe → Detect → Investigate → Hypothesize → Propose → Authorize → Execute → Verify → Learn

Vykronis is an autonomous operations platform: it ingests observability events, correlates them to surface incidents, uses **tool-constrained AI agents** to investigate root cause, enforces **policy before any action**, and remediates — then **verifies** the problem is gone.

This is **not** a monitoring dashboard and **not** a chatbot. Every technology below has a real, singular job.

## Stack

| Area | Choice |
|---|---|
| Language / runtime | **Java 25** |
| Framework | Spring Boot 4.1.x, Spring Cloud 2025.1.x (Oakwood) |
| Build | Maven multi-module, Java 25 toolchain |
| Streaming | Apache Kafka (KRaft) + Kafka Streams |
| Storage | PostgreSQL + Flyway, Redis |
| AI | Spring AI, **pluggable** (default Ollama, local + free; `none` = rule-based) |
| Observability | OpenTelemetry, Micrometer, Prometheus, Grafana, JFR streaming |
| Search | OpenSearch (later phases) |
| Security | Spring Security, OAuth2/OIDC (Keycloak) |
| UI | Next.js (App Router) |
| Infrastructure | Docker Compose (local), kind/k3d + Helm (Kubernetes, Phase 7) |

## Repo layout

```
contracts/                 JSON Schema + Java records for events
libs/common/               trace IDs, API errors, Kafka headers (thin)
platform/api-gateway/
platform/ingestion-service/
platform/event-service/
platform/correlation-engine/
platform/incident-service/
platform/agent-orchestrator/
platform/policy-service/
platform/remediation-service/
demo/order-service/
demo/payment-service/
demo/inventory-service/
demo/notification-service/
ui/web/                    Next.js
infra/compose/             Docker Compose profiles
infra/k8s/                 Helm + kind/k3d (Phase 7)
```

Base package: `io.vykronis`

## Requirements

- **Java 25 JDK** (toolchain + runtime) — all services run on 25.
- Docker Desktop with Compose v2.
- Node.js 20+ (for the Next.js UI, Phase 1+).

## Local-first

Vykronis runs entirely locally and costs **$0** in required paid services:
- Local LLM via Ollama (no paid OpenAI required).
- Local Kafka, Postgres, Redis via Docker Compose.
- Local Kubernetes via kind/k3d in later phases.

Secrets live in `.env` (never committed); a `.env.example` is provided.

## Phase status

| Phase | Status |
|---|---|
| P0 Scaffolding | ✅ health-only JVMs + Compose core |
| P1 Ingest + incident | ⬜ |
| P2 Kafka Streams correlation | ⬜ |
| P3 Observability depth | ⬜ |
| P4 AI investigation | ⬜ |
| P5 Policy + approval | ⬜ |
| P6 Remediate + verify | ⬜ |
| P7 K8s + CI | ⬜ |

## Run locally (Phase 0)

Prerequisites: JDK 25, Docker Desktop (Compose v2), Maven 3.9+.

**1. Start the infra core (Kafka, Postgres, Redis):**

```powershell
docker compose -f infra/compose/docker-compose.yml up -d
```

**2. Build the whole multi-module project:**

```powershell
mvn clean package -DskipTests
```

**3. Run a service (each on its own terminal) and check health:**

```powershell
mvn -pl platform/incident-service spring-boot:run
```

then visit `http://localhost:8084/actuator/health` → `{"status":"UP"}`.

Ports (plan §14): gateway `8080`, ingestion `8081`, event `8082`, correlation `8083`,
incident `8084`, orchestrator `8085`, policy `8086`, remediation `8087`.

**4. Or run everything in Docker (builds each service image):**

```powershell
# one-time: shared slim jlink JRE (with a baked AppCDS archive) used as the
# runtime stage of every service image.
docker build -f infra/docker/runtime.Dockerfile -t vykronis/runtime:local .
docker compose -f infra/compose/docker-compose.yml -f infra/compose/docker-compose.apps.yml --profile apps up -d --build
```

**5. Health smoke check across all 8 services:**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/health-check.ps1
```
