#!/bin/sh
set -u
ING=http://ingestion-service:8081/api/ingest/events
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
echo "=== bursting 14 PROD payment-service events (error_rate ~70) into obs.metrics ==="
i=1
while [ $i -le 14 ]; do
  RATE=$((62 + (i % 9)))            # 62..70
  ID=$(cat /proc/sys/kernel/random/uuid)
  BODY='{"id":"'$ID'","timestamp":"'$(now)'","source":"demo-generator","serviceId":"payment-service","env":"PROD","type":"METRIC","payload":{"error_rate":'$RATE',"error_count":8,"latency_ms":1200}}'
  curl -s -o /dev/null --max-time 20 -X POST $ING -H Content-Type:application/json -d "$BODY"
  i=$((i + 1))
done
echo "burst sent"
echo "=== polling for a FRESH OPEN incident (up to 150s) ==="
for i in $(seq 1 15); do
  sleep 10
  OPEN=$(curl -s --max-time 30 http://incident-service:8084/api/incidents?status=OPEN | grep -oE '"incidentId":"[^"]+"' | head -1 | cut -d'"' -f4)
  echo "poll $i: OPEN=$OPEN"
  case "$OPEN" in *[![:space:]]*) break;; esac
done
if [ -n "${OPEN:-}" ]; then echo "FRESH_OPEN=$OPEN"; else echo "NO_OPEN_YET"; fi