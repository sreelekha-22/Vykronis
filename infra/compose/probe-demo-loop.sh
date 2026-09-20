#!/bin/sh
set -u
INC=46aad0fa-2647-4a3a-a343-431d2046a162
API=http://incident-service:8084/api/incidents/$INC
SUB='{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'

st() { curl -s --max-time 30 $API | grep -oE 'status[^,]*' | head -1; }

echo "=== DEMO LOOP on $INC ==="
echo "=== [1] investigate ==="
curl -s --max-time 120 -o /dev/null -w "POST->%{http_code}\n" -X POST $API/investigate -H Content-Type:application/json -d {}
for i in $(seq 1 20); do sleep 5; S=$(st); echo "poll $i: $S"; case "$S" in *HYPOTHESIS_READY*|*FAILED*) break;; esac; done

echo "=== [2] hypothesis ==="
curl -s --max-time 30 $API | grep -oE 'hypothesis[^,]{0,120}|source[^,]{0,30}' | head -4

echo "=== [3] request remediation (operator ops / approver) ==="
curl -s --max-time 120 -o /dev/null -w "POST->%{http_code}\n" -X POST $API/remediation -H Content-Type:application/json -d "$SUB"
for i in $(seq 1 15); do sleep 5; S=$(st); echo "poll $i: $S"; case "$S" in *AWAITING_APPROVAL*|*REMEDIATING*|*FAILED*|*RESOLVED*) break;; esac; done

echo "=== [4] policy decision recorded ==="
curl -s --max-time 30 $API | grep -oE 'policyDecision[^,]*|requestedAt[^,]*' | head -3

echo "=== [5] approve (same approver) ==="
curl -s --max-time 120 -o /dev/null -w "POST->%{http_code}\n" -X POST $API/approve -H Content-Type:application/json -d "$SUB"
for i in $(seq 1 20); do sleep 6; S=$(st); echo "poll $i: $S"; case "$S" in *RESOLVED*|*VERIFYING*|*FAILED*) break;; esac; done

echo "=== [final] detail ==="
curl -s --max-time 30 $API | grep -oE 'status[^,]*|env[^,]{0,20}|policyDecision[^,]*|approvedBy[^,]*|approvedAt[^,]*|remediation[^,]{0,60}|appliedAt[^,]*|resolvedAt[^,]*' | head -16
echo DONE