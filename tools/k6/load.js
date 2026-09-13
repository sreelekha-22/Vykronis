// Vykronis demo traffic generator (k6).
//
// Feeds the platform through the REAL HTTP ingest path instead of Kafka:
//   * steady     — healthy METRIC events for the order/inventory services
//   * burst      — 45-65% error-rate bursts for payment-service, long enough to
//                  trip the correlation engine (error_rate >= 20 for the 60s
//                  window, error_count >= 5) and open a PROD incident
//   * deploys    — deployment events via the agent-orchestrator demo endpoint,
//                  so incidents are attributed to a version on the timeline
//
// Run from the repo root (app services must be up first):
//   docker compose -f infra/compose/docker-compose.yml \
//                 -f infra/compose/docker-compose.apps.yml \
//                 -f infra/compose/docker-compose.load.yml \
//                 --profile apps --profile load up k6
// Or against a local/port-forwarded stack:
//   INGEST_URL=http://localhost:8081 ORCH_URL=http://localhost:8085 \
//     k6 run tools/k6/load.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const INGEST_URL = __ENV.INGEST_URL || 'http://localhost:8081';
const ORCH_URL = __ENV.ORCH_URL || 'http://localhost:8085';

const SERVICES = {
  payment: 'payment-service',
  order: 'order-service',
  inventory: 'inventory-service',
};

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: 3,
      duration: '60s',
    },
    deploys: {
      executor: 'constant-vus',
      vus: 1,
      duration: '60s',
      startTime: '5s',
    },
    burst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 3 },
        { duration: '50s', target: 3 },
        { duration: '5s', target: 0 },
      ],
      startTime: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
  },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function metricEvent(serviceId, burst) {
  const errorRate = burst ? 45 + Math.random() * 20 : 0.5 + Math.random() * 3;
  const latency = burst ? 900 + Math.random() * 600 : 40 + Math.random() * 80;
  return {
    id: uuid(),
    timestamp: new Date().toISOString(),
    source: 'k6-load',
    serviceId,
    env: 'PROD',
    type: 'METRIC',
    payload: {
      error_rate: Number(errorRate.toFixed(2)),
      error_count: burst ? 1 + Math.floor(Math.random() * 4) : Math.floor(Math.random() * 3),
      latency_ms: Number(latency.toFixed(1)),
    },
    traceId: null,
  };
}

function ingest(event) {
  const response = http.post(`${INGEST_URL}/api/ingest/events`, JSON.stringify(event), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(response, {
    'ingest accepted (202)': (r) => r.status === 202,
    'echoes the event id': (r) => r.json('id') === event.id,
  });
}

export function steady() {
  const serviceId = [SERVICES.order, SERVICES.inventory][__VU % 2];
  ingest(metricEvent(serviceId, false));
  sleep(0.4 + Math.random() * 0.6);
}

export function deploys() {
  const serviceId = [SERVICES.payment, SERVICES.order, SERVICES.inventory][__ITER % 3];
  const version = `1.${1 + (__ITER % 3)}.${__ITER % 5}`;
  const response = http.post(
    `${ORCH_URL}/api/demo/deploy/${serviceId}?version=${version}&env=PROD`,
    null,
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(response, {
    'deploy recorded (200)': (r) => r.status === 200,
    'deploymentId returned': (r) => r.has('deploymentId'),
  });
  sleep(3);
}

export function burst() {
  const burstTarget = [SERVICES.payment, SERVICES.payment, SERVICES.payment][__VU % 1];
  ingest(metricEvent(burstTarget, true));
  sleep(0.3 + Math.random() * 0.4);
}

export default function () {
  // Unused: k6 runs exported scenario functions directly; kept so `k6 run
  // tools/k6/load.js --execution ...` or a bare `k6 run` stays harmless.
  sleep(1);
}