# Phase 1 Demo — Ingest + Incident

## Overview
This demo shows the closed loop: error burst creates an incident visible in the UI.

## Prerequisites
- Java 25
- Docker + Docker Compose
- Maven (via wrapper)

## Steps

### 1. Start Infrastructure
```bash
cd Ob_Autonomous_Platform
docker compose -f infra/compose/docker-compose.yml up -d
```
Wait for Kafka, Postgres, Redis to be healthy.

### 2. Start Services
```bash
./mvnw clean install -DskipTests
.\scripts\run-local.bat
```

### 3. Verify Health
```bash
.\scripts\health-check.ps1
```

### 4. Trigger Error Burst
Start the `demo-service` with the `demo-traffic` profile (auto-generation is profile-gated so a plain boot never spams metrics):
```bash
mvn -pl platform/demo-service spring-boot:run -Dspring-boot.run.profiles=demo-traffic
```
`DemoRunner` then produces error burst events to Kafka topic `obs.metrics`.

Or manually:
```bash
curl -X POST http://localhost:8081/api/ingest/events \
  -H "Content-Type: application/json" \
  -d '{
    "source": "manual-test",
    "serviceId": "demo-service",
    "env": "PROD",
    "type": "METRIC",
    "payload": {
      "error_rate": 25.0,
      "error_count": 15
    }
  }'
```

### 5. Check Incidents
Open browser:
```
http://localhost:8080/api/incidents
```

Or via API:
```bash
curl http://localhost:8084/api/incidents
```

Expected: Incident with status `OPEN` and error count > threshold (e.g., 20%).

### 6. View Incident Detail
Click an incident in the UI to see timeline and details.

## Architecture
```
DemoRunner → Kafka (obs.metrics) → EventService → Postgres
                              → IncidentService → Postgres
                              → API Gateway → UI
```

## Ports
| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Ingestion | 8081 |
| Event | 8082 |
| Correlation | 8083 |
| Incident | 8084 |
| Demo | 8085 |

## Troubleshooting
- **Kafka not ready**: Wait 30s after `docker compose up`
- **No incidents**: Check `demo-service` logs for traffic generation
- **UI empty**: Verify incident-service is running on port 8084
