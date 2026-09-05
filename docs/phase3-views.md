# Phase 3 — Two Views of the System

**Grafana is the operator view. The Vykronis UI is the incident view.**

This is the operational contract of Vykronis: the two UIs are NOT two
implementations of the same thing. They answer different questions, from
different data, for different people.

## 1. Grafana — the operator view

Grafana (observability compose profile, port **3000**) watches the **fleet** in
real time. It is where an operator monitors infrastructure health and
capacity — dashboards, metrics graphs, alerting, and per-target scrapes.

| Instrument | Role |
|---|---|
| **OTel Collector** (`otel-collector` container) | receives OTLP (gRPC 4317 / HTTP 4318) once Micrometer/OTel are wired in (`common-observability`); exposes a Prometheus-format metrics endpoint on `8889/metrics` |
| **Prometheus** (`prometheus` container, port 9090) | stores and queries metrics; scrapes the OTel Collector (`8889`) **and** the services' `/actuator/prometheus` endpoints directly; targets are visible on `/targets` |
| **Grafana** (`grafana` container, port 3000) | auto-provisions the Prometheus data source; dashboards live here |

Open `http://localhost:3000` (default admin admin) → **Explore / Dashboards**.
You see error rates, latencies, GC activity, and live targets — fleet health.

## 2. Vykronis UI — the incident view

The Vykronis UI (`ui/web`, Next.js App Router) answers **"what happened for
this incident?"**. It is the view an engineer or an agent uses to assemble a
timeline of evidence around one incident.

| Route | Content |
|---|---|
| `/` | Incident list from the incident service |
| `/incidents/{id}` | Incident detail: detected window, severity/status, and the **timeline of evidence** (TRACE / LOG / METRIC / JFR refs) |

The timeline is **not** read from Grafana. The UI calls the event-service
search API (`/api/search/events`, proxied by the api-gateway on port **8080**)
which queries **OpenSearch** (`obs-events` index) for events inside the
incident's time window. Each evidence entry renders an opener ref:

- **TRACE** → `trace:<traceId>` evidence-detail ref (carries `traceId` for
  cross-referencing the distributed trace)
- **LOG** → `log:<source>` evidence-detail ref
- **JFR** → `jfr:<fileName>` evidence-detail ref (JFR chunks streamed on the
  `obs.jfr` topic by the demo JFR exporter)
- other types → generic `{type}:{source}` ref

### Source of truth

```
                     persist (idempotent)          index
 obs.metrics ──▶ event-service ──▶ Postgres  ──▶ OpenSearch (obs-events)
                    (consumer)      (truth)          (search/evidence)
                                                        │
                                                        ▼
                             api-gateway ──▶ Vykronis UI timeline
```

Postgres is the system of record; OpenSearch is an optional search/evidence
index (if it is down, events still persist and the service stays healthy —
only search is unavailable). No timeline data lives in Grafana/Prometheus.

## 3. Which view do I open?

| Question | Go to |
|---|---|
| "Is the fleet healthy right now? What are current error rates / targets?" | **Grafana** (3000) |
| "Is there an incident, and what evidence explains it?" | **Vykronis UI** (8080) |
| "Within which window, on which service, and what trace/log/JFR refs?" | **Vykronis UI → `/incidents/{id}`** |
| "Did my metrics actually arrive?" | Prometheus `/targets` (9090) and Grafana Explore |
| "Is an event persisted but not searchable?" | event-service logs + Postgres; OpenSearch optional |

## 4. Running both views

```bash
cd Ob_Autonomous_Platform

# core + observability + search infrastructure
docker compose -f infra/compose/docker-compose.yml --profile observability --profile search up -d

# services (gateway on 8080, event-service on 8082, incident-service on 8084)
.\scripts\run-local.bat

# optional: generate error-burst + JFR traffic
mvn -pl platform/demo-service spring-boot:run -Dspring-boot.run.profiles=demo-traffic,jfr-stream

# Vykronis UI (dev)
cd ui\web
npm install
npm run dev          # http://localhost:3000 … next dev default port
```

> Note: with `npm run dev` Next uses port 3000 (same as Grafana). Port-forward
> the UI to 3100 to avoid the conflict and keep Grafana on 3000:
> `npm run dev -- -p 3100`. The API base URL is `VYKRONIS_API_URL`
> (defaults to `http://localhost:8080`).

Production build of the UI:

```bash
cd ui\web
npm run build && npm start
```

Verify health of the backend views:

```bash
.\scripts\health-check.ps1     # services UP
.\scripts\search-check.ps1     # OpenSearch accepts and returns docs
```

## 5. Phased view of what was shipped (so far)

| Unit | What it observes |
|---|---|
| observability/profile (Grafana+Prometheus) | fleet-level metrics; operator view |
| `obs.traces` / `obs.jfr` topics + demo JFR exporter | trace and JFR telemetry streams |
| search profile (OpenSearch) + event-service search APIs | searchable evidence per incident window |
| Vykronis UI timeline (Unit 7) | incident-scoped evidence timeline with TRACE/LOG/JFR refs |

Phase 4+ will build the agent investigation experience **on top of the Vykronis
incident view** — the operator never leaves Grafana for incident work, and the
incident view never depends on Grafana.

## Troubleshooting

- **UI route empty / unknown incident** — confirm incident-service (8084) and
  that the incident exists: `curl http://localhost:8084/api/incidents`.
- **Timeline has no evidence refs** — confirm event-service is consuming
  (`spring.kafka.listener.auto-startup` true in prod) and OpenSearch is up; if
  `obs-events` is missing, event-service recreates it on boot.
- **Git-fast feedback**: run `npm test` in `ui/web` (Vitest) and
  `npm run lint` (tsc) before touching the UI.