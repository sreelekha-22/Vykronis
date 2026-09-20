#!/bin/sh
INC=http://incident-service:8084/api/incidents/7ab26b0f-20b8-49ca-888c-90b47322a66b
echo "=== policy health ==="
curl -s -o /dev/null --max-time 20 http://policy-service:8086/actuator/health && echo POLICY_OK || echo POLICY_FAIL
echo "=== remediation POST (direct, 180s) ==="
curl -s --max-time 180 -o /tmp/remed.out -w "status=%{http_code}\n" -X POST $INC/remediation -H Content-Type:application/json -d {}
echo "--- body ---"
head -c 500 /tmp/remed.out 2>/dev/null; echo
sleep 2
echo "=== state after remediation ==="
curl -s --max-time 30 $INC | grep -oE 'status[^,]*|remediation[^,]{0,40}'