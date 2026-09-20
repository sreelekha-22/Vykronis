#!/bin/sh
INC=e1646546-f27f-458d-a1f5-48fcef39f836
API=http://incident-service:8084/api/incidents/$INC
SUB='{"subject":{"name":"ops","roles":["vykronis-approver"],"service":false}}'
echo "=== approve retry ==="
curl -s --max-time 120 -o /dev/null -w "POST->%{http_code}\n" -X POST $API/approve -H Content-Type:application/json -d "$SUB"
for i in $(seq 1 10); do sleep 6; S=$(curl -s --max-time 30 $API | grep -oE 'status[^,]*' | head -1); echo "poll $i: $S"; case "$S" in *REMEDIATING*|*RESOLVED*|*FAILED*|*VERIFYING*) break;; esac; done
echo "=== final ==="
curl -s --max-time 30 $API | grep -oE 'status[^,]*|approvedBy[^,]*|approvedAt[^,]*|policyDecision[^,]*|remediation[^,]{0,60}|resolvedAt[^,]*'