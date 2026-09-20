#!/bin/sh
set -u
INC=46aad0fa-2647-4a3a-a343-431d2046a162
API=http://incident-service:8084/api/incidents/$INC
SUB='{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
st() { curl -s --max-time 30 $API | grep -oE 'status[^,]*' | head -1; }

echo "=== current: $(st) ==="
echo "=== [3] request remediation (ops/approver) ==="
curl -s --max-time 120 -w "\nPOST->%{http_code}\n" -X POST $API/remediation -H Content-Type:application/json -d "$SUB" | head -c 500
echo
for i in $(seq 1 12); do sleep 5; S=$(st); echo "poll $i: $S"; case "$S" in *AWAITING_APPROVAL*|*REMEDIATING*|*FAILED*|*RESOLVED*) break;; esac; done

echo "=== [4] policyDecision ==="
curl -s --max-time 30 $API | grep -oE 'policyDecision[^,]*|requestedAt[^,]*' | head -3

echo "=== [5] approve (ops/approver) ==="
curl -s --max-time 120 -w "\nPOST->%{http_code}\n" -X POST $API/approve -H Content-Type:application/json -d "$SUB" | head -c 500
echo
for i in $(seq 1 20); do sleep 6; S=$(st); echo "poll $i: $S"; case "$S" in *RESOLVED*|*VERIFYING*|*FAILED*) break;; esac; done

echo "=== [final] detail ==="
curl -s --max-time 30 $API | grep -oE 'status[^,]*|env[^,]{0,20}|policyDecision[^,]*|approvedBy[^,]*|approvedAt[^,]*|remediation[^,]{0,60}|resolvedAt[^,]*' | head -16
echo DONE