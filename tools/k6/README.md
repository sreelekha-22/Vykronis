# Vykronis demo traffic generator (k6)

`tools/k6/load.js` drives the platform through the real HTTP ingest path
(`POST /api/ingest/events` on ingestion-service) rather than writing to Kafka
directly, exactly like production telemetry. It runs three scenarios:

| Scenario | Window   | Purpose                                                        |
|----------|----------|----------------------------------------------------------------|
| steady   | 0–60s    | Healthy METRIC events for order-service / inventory-service    |
| deploys  | 5–65s    | Deployment events (agent-orchestrator demo endpoint) for the   |
|          |          | GlobalKTable join so incidents get version attribution         |
| burst    | 30–90s   | 45–65% error-rate burst on payment-service → trips correlation |
|          |          | (`error_rate >= 20`, `error_count >= 5`) → PROD incident       |

The throttles force `http_req_failed < 10%` so accidental non-202s fail the run.

## Options

1. **Through the compose stack** (no k6 locally):
   ```
   docker compose -f infra/compose/docker-compose.yml \
                 -f infra/compose/docker-compose.apps.yml -f infra/compose/docker-compose.load.yml \
                 up -d --build
   docker compose -f infra/compose/docker-compose.yml \
                 -f infra/compose/docker-compose.apps.yml -f infra/compose/docker-compose.load.yml \
                 --profile apps --profile load up k6
   ```
2. **Standalone binary** against a running (or port-forwarded) stack:
   ```
   INGEST_URL=http://localhost:8081 ORCH_URL=http://localhost:8085 k6 run tools/k6/load.js
   ```

After the run completes expect: incidents on the dashboard (incident-service
`/api/incidents/**` via the gateway), a `remediation_learn` row if the policy
auto-approves, and (kubernetes target) a `rollout restart`/`undo` recorded in
the incident timeline.

CI runs `k6 inspect tools/k6/load.js` on every push so the script can never
silently rot (see `.github/workflows/ci.yml`, `extensions` job).