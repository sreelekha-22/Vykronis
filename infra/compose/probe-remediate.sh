#!/bin/sh
INC=http://incident-service:8084/api/incidents/7ab26b0f-20b8-49ca-888c-90b47322a66b
echo "=== remediation POST (direct) ==="
curl -s --max-time 110 -o /tmp/remed.out -w "status=%{http_code}\n" -X POST $INC/remediation -H Content-Type:application/json -d {} || echo POSTFAIL
cat /tmp/remed.out | head -c 400; echo
sleep 3
echo "=== state after remediation ==="
curl -s --max-time 30 $INC | grep -oE 'status[^,]*|remediation[^,]{0,40}'
echo "=== approve POST (direct) ==="
curl -s --max-time 110 -o /tmp/app.out -w "status=%{http_code}\n" -X POST $INC/approve -H Content-Type:application/json -d {} || echo POSTFAIL
cat /tmp/app.out | head -c 400; echo
sleep 3
echo "=== final state ==="
curl -s --max-time 30 $INC | grep -oE 'status[^,]*|hypothesis[^,]{0,80}|remediation[^,]{0,40}|approvedAt[^,]*'