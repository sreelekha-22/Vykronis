#!/bin/sh
curl -s --max-time 25 http://incident-service:8084/api/incidents/7ab26b0f-20b8-49ca-888c-90b47322a66b | grep -oE 'hypothesis[^,]*|status[^,]*|remediation[^,]*|detectedAt[^,]*' | head -10