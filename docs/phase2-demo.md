# Phase 2 Demo — Kafka Streams Correlation

## Overview
A faulty Payment deploy yields a correlated incident in ~1 minute **without a human create**. The correlation engine windows the error burst, attributes it to the deployment, and the incident service opens an incident you can open in the UI.

## Prerequisites
- Java 25, Docker + Docker Compose, Maven (wrapper)
- Phase 1 services built and running

## Steps

### 1. Start infrastructure + services
```bash
cd Ob_Autonomous_Platform
docker compose -f infra/compose/docker-compose.yml up -d
.\scripts\run-local.bat   # starts all 12 JVM services
.\scripts\health-check.ps1
```

### 2. Simulate a faulty Payment deployment
Trigger a deployment record onto `obs.deployments`:
```bash
curl -X POST "http://localhost:8085/api/demo/deploy/payment-service?version=1.4.2&env=PROD"
# => {"deploymentId":"...","serviceId":"payment-service","version":"1.4.2","env":"PROD"}
```

### 3. Let the error burst flow
`run-local.bat` does **not** launch the demo-service automatically. Start it with the `demo-traffic` profile (auto-generation is profile-gated so the service never spams metrics on a plain boot):
```bash
mvn -pl platform/demo-service spring-boot:run -Dspring-boot.run.profiles=demo-traffic
```
`DemoRunner` emits a ~6-sample error burst for `payment-service` (error_rate 45–65%) every 12 iterations (~6 seconds). The correlation engine:
- windows `obs.metrics` into 1-minute tumbling windows
- aggregates `error_count`, `error_rate`, latency
- breaches the `error_rate >= 20` threshold → emits `IncidentCandidate` to `obs.alerts`
- joins against the `obs.deployments` GlobalKTable → attributes `deploymentId 1.4.2`

### 4. Verify the incident in the UI
Open the incident UI:
```
http://localhost:8080/api/incidents
```
You should see an `OPEN` incident titled e.g. `High error rate on payment-service (HIGH)` with `errorRate` > 20 and a deployment id on its metadata.

Or via API:
```bash
curl http://localhost:8084/api/incidents
```

## How it works

```
DemoRunner ──obs.metrics──▶ CorrelationEngine (Kafka Streams)
                              │ windowed aggregation (1 min)
                              │ threshold breach
                              │ join obs.deployments (GlobalKTable)
                              ▼
                           obs.alerts  ──▶ IncidentService (consumer)
                                              │ open (or update) incident
                                              ▼
                                           Postgres incidents table
                                              │
                                              ▼
                              api-gateway ─▶ incident UI (list/detail)
```

## Kafka topics used
| Topic | Producer | Consumer |
|-------|----------|----------|
| `obs.metrics` | DemoRunner | correlation-engine (streams) |
| `obs.deployments` | DemoService `/deploy` | correlation-engine (GlobalKTable) |
| `obs.alerts` | correlation-engine | incident-service |

## Tuning knobs (application.properties of correlation-engine)
- `correlation.window.minutes=1` — tumbling window size
- `correlation.window.grace.seconds=5` — late-event grace
- `correlation.error-rate-threshold=20.0` — error rate that triggers an incident
- `correlation.error-count-threshold=5` — cumulative error count trigger

## Troubleshooting
- **No incident**: check correlation-engine logs for window aggregation; confirm Kafka topics exist and `obs.metrics` carries `error_rate`.
- **Deployment id missing on incident**: make sure the `/deploy` call ran before (or around) the burst so the GlobalKTable has the deployment.
- **UI empty**: confirm incident-service health on `localhost:8084/actuator/health`.
