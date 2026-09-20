#!/bin/sh
set -u
GW=http://api-gateway:8080
INC=7ab26b0f-20b8-49ca-888c-90b47322a66b

hdr() { echo; echo "=== $1 ==="; }

hdr "[0] search route check"
curl -s --max-time 40 -o /dev/null -w "search -> %{http_code} (%{time_total}s)\n" "$GW/api/search/events?from=2026-09-18T16:00:00Z&to=2026-09-18T19:00:00Z&serviceId=payment-service"

hdr "[1] investigate $INC (max-time 120s)"
curl -s --max-time 120 -w "\ninvestigate POST -> %{http_code}\n" -X POST "$GW/api/incidents/$INC/investigate" -H "Content-Type: application/json" -d "{}"

hdr "[2] poll status up to 120s"
for i in $(seq 1 20); do
  sleep 6
  S=$(curl -s --max-time 40 "$GW/api/incidents/$INC" | grep -oE '"status"[^,]*' | head -1)
  H=$(curl -s --max-time 40 "$GW/api/incidents/$INC" | grep -oiE '"hypothesis"[^,]*' | head -1)
  echo "poll $i: $S | $H"
  case "$S" in *RESOLVED*|*HYPOTHESIS_READY*|*REMEDIATION*|*AUTO_FAILED*) break ;; esac
done

hdr "[3] request remediation (max-time 90s)"
curl -s --max-time 90 -w "\nremediation POST -> %{http_code}\n" -X POST "$GW/api/incidents/$INC/remediation" -H "Content-Type: application/json" -d "{}"

hdr "[4] approve (max-time 60s)"
curl -s --max-time 60 -w "\napprove POST -> %{http_code}\n" -X POST "$GW/api/incidents/$INC/approve" -H "Content-Type: application/json" -d "{}"

hdr "[5] final state"
curl -s --max-time 40 "$GW/api/incidents/$INC" | tr -d '[]' | tr '}' '}\n' | grep -oE '"status"[^,]*|"hypothesis"[^,]*|"remediation"[^,]*|"approvedAt"[^,]*' | head -12

echo
echo DONE