#!/bin/sh
curl -s --max-time 30 http://incident-service:8084/api/incidents | tr -d '[]' | tr '}' '}\n' | grep -oE 'incidentId[^,]*|status[^,]*|serviceId[^,]*|detectedAt[^,]*'