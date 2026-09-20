#!/bin/sh
set -u
INC=70d578a3-1bad-4094-9acd-e7a10d5d2e1e
API=http://incident-service:8084/api/incidents/$INC

st() { curl -s --max-time 30 $API | grep -oE 'status[^,]*' | head -1; }

echo "=== [1] investigate $INC (via incident-service) ==="
curl -s --max-time 120 -o /tmp/o -w "POST->%{http_code}\n" -X POST $API/investigate -H Content-Type:application/json -d {}
echo "correlation+investigation running... polling"
for i in $(seq 1 25); do
  sleep 8
  S=$(st)
  echo "poll $i: $S"
  case "$S" in *HYPOTHESIS_READY*|*FAILED*|*AWAITING_APPROVAL*|*REMEDIATING*|*RESOLVED*) break;; esac
done

case "$S" in
  *HYPOTHESIS_READY*)
    echo "=== [2] request remediation ==="
    curl -s --max-time 120 -o /tmp/o -w "POST->%{http_code}\n" -X POST $API/remediation -H Content-Type:application/json -d {}
    for i in $(seq 1 10); do
      sleep 8
      S=$(st)
      echo "poll $i: $S"
      case "$S" in *AWAITING_APPROVAL*|*REMEDIATING*|*FAILED*|*VERIFYING*|*RESOLVED*) break;; esac
    done
    ;;
esac

case "$S" in
  *AWAITING_APPROVAL*)
    echo "=== [3] approve ==="
    curl -s --max-time 60 -o /tmp/o -w "POST->%{http_code}\n" -X POST $API/approve -H Content-Type:application/json -d {}
    for i in $(seq 1 20); do
      sleep 8
      S=$(st)
      echo "poll $i: $S"
      case "$S" in *REMEDIATING*|*VERIFYING*|*RESOLVED*|*FAILED*) break;; esac
    done
    ;;
esac

echo "=== [final] detail ==="
curl -s --max-time 30 $API | grep -oE 'status[^,]*|hypothesis[^,]{0,80}|remediationCommandId[^,]*|policyDecision[^,]*|appliedAt[^,]*|resolvedAt[^,]*' | head -14
echo DONE