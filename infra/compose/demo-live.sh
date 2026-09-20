#!/bin/sh
# =====================================================================
#  VYKRONIS LIVE DEMO — one file, run from your Windows box
#  Shows: ingest burst -> detect OPEN -> investigate -> hypothesis ->
#         policy REQUIRE_APPROVAL -> AWAITING_APPROVAL -> approve ->
#         REMEDIATING -> (RESOLVED when the executer infra is started)
# =====================================================================
set -u

INC=http://incident-service:8084/api/incidents
ING=http://ingestion-service:8081/api/ingest/events
SUB='{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
st()  { curl -s --max-time 30 $INC/$1 | grep -oE 'status[^,]*' | head -1; }

echo "================================================================"
echo " [0] healthy demo traffic exists; minting a FRESH error burst   "
echo "================================================================"
i=1
while [ $i -le 14 ]; do
  RATE=$((62 + (i % 9)))
  BODY='{"id":"'$(cat /proc/sys/kernel/random/uuid)'","timestamp":"'$(now)'","source":"demo-generator","serviceId":"payment-service","env":"PROD","type":"METRIC","payload":{"error_rate":'$RATE',"error_count":8,"latency_ms":1200}}'
  curl -s -o /dev/null --max-time 20 -X POST $ING -H Content-Type:application/json -d "$BODY"
  i=$((i + 1))
done
echo "   burst sent -> correlation engine should OPEN a new incident soon"

echo
echo "================================================================"
echo " [1] waiting for the OPEN incident (usually < 20s)...            "
echo "================================================================"
NEWID=""
for i in $(seq 1 15); do
  sleep 10
  NEWID=$(curl -s --max-time 30 "$INC?status=OPEN" | grep -oE '"incidentId":"[^"]+"' | head -1 | cut -d'"' -f4)
  [ -n "$NEWID" ] && break
done
[ -z "$NEWID" ] && { echo "   no new OPEN incident appeared - aborting"; exit 1; }
echo "   NEW OPEN INCIDENT: $NEWID"

echo
echo "================================================================"
echo " [2] investigate  -> hypothesis                                   "
echo "================================================================"
curl -s --max-time 120 -o /dev/null -w "   investigate POST -> %{http_code}\n" -X POST $INC/$NEWID/investigate -H Content-Type:application/json -d {}
for i in $(seq 1 20); do sleep 5; S=$(st $NEWID); echo "   poll $i: $S"; case "$S" in *HYPOTHESIS_READY*|*FAILED*) break;; esac; done
curl -s --max-time 30 $INC/$NEWID | grep -oE 'hypothesis[^,]{0,120}' | head -1 | sed 's/^/   /'

echo
echo "================================================================"
echo " [3] remediation (ops/vykronis-approver)  -> policy gate         "
echo "================================================================"
curl -s --max-time 120 -o /dev/null -w "   remediation POST -> %{http_code}\n" -X POST $INC/$NEWID/remediation -H Content-Type:application/json -d "$SUB"
for i in $(seq 1 12); do sleep 5; S=$(st $NEWID); echo "   poll $i: $S"; case "$S" in *AWAITING_APPROVAL*|*REMEDIATING*|*FAILED*|*RESOLVED*) break;; esac; done
curl -s --max-time 30 $INC/$NEWID | grep -oE 'policyDecision[^,]*' | sed 's/^/   /'

echo
echo "================================================================"
echo " [4] approve (same human approver)  -> remediation command       "
echo "================================================================"
# approve can transiently 500 on the kafka send; retry until it lands
for attempt in 1 2 3; do
  curl -s --max-time 120 -o /dev/null -w "   approve POST (try $attempt) -> %{http_code}\n" -X POST $INC/$NEWID/approve -H Content-Type:application/json -d "$SUB"
  CHANGED=""
  for i in $(seq 1 8); do sleep 6; S=$(st $NEWID); echo "   poll $i: $S"; case "$S" in *REMEDIATING*|*VERIFYING*|*RESOLVED*|*FAILED*) CHANGED=1; break;; esac; done
  [ -n "$CHANGED" ] && break
done

echo
echo "================================================================"
echo " [5] FINAL state of $NEWID"
echo "================================================================"
curl -s --max-time 30 $INC/$NEWID | grep -oE 'status[^,]*|env[^,]{0,20}|policyDecision[^,]*|approvedBy[^,]*|approvedAt[^,]*|remediation[^,]{0,60}|resolvedAt[^,]*' | sed 's/^/   /'
echo
echo "   Note: if parked at REMEDIATING, run the infra profiles to see:"
echo "     docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile apps --profile observability --profile search --profile ai up -d --build"
echo "DONE"