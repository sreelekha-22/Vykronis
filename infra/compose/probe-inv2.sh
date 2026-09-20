#!/bin/sh
INC=46aad0fa-2647-4a3a-a343-431d2046a162
API=http://incident-service:8084/api/incidents/$INC
echo "=== retry investigate (http_code + body head) ==="
curl -s --max-time 60 -w "\nstatus=%{http_code}\n" -X POST $API/investigate -H Content-Type:application/json -d {} | head -c 600