#!/usr/bin/env bash
# =====================================================================
#  VYKRONIS - Oracle Cloud Always-Free deployment bootstrap (ARM Ampere)
#  Target: VM.Standard.A1.Flex, 2 OCPU / 12 GB RAM, Ubuntu 24.04
#  Expected total stack memory: ~5-6GB (see docker-compose.lean.yml)
#  Run as root on the VM:  sudo bash deploy.sh
# =====================================================================
set -euo pipefail

cd "$(dirname "$0")"

echo "==> 0) Docker engine present?"
if ! docker info >/dev/null 2>&1; then
  echo "    Installing Docker (docker-ce + compose plugin)..."
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
fi
docker --version
docker compose version

echo "==> 1) One-time base image (shared slim jlink JRE for all services)"
docker build -f ../docker/runtime.Dockerfile -t vykronis/runtime:local .

echo "==> 2) Building app images (ARM - first build takes 20-40 min)"
echo "    Log to /tmp/vykronis-build.log"
docker compose \
  -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml \
  --profile apps build > /tmp/vykronis-build.log 2>&1
echo "    Build finished."

echo "==> 3) Bringing the full stack up (13 services, volumes preserved)"
docker compose \
  -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml \
  --profile apps up -d

echo "==> 4) Waiting for the gateway + incident-service to be healthy (up to 180s)..."
for i in $(seq 1 36); do
  sleep 5
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/incidents || true)
  [ "$code" = "200" ] && { echo "    Gateway healthy (HTTP $code)."; break; }
  [ "$i" = "36" ] && echo "    WARNING: still not healthy - check: docker compose ps / logs"
done

echo
echo "================================================================"
echo " DONE. Open in a browser:"
echo "   http://$(hostname -I | awk '{print $1}'):8080/"
echo " (for a public IP, open Oracle Security List rule: TCP 8080)"
echo "================================================================"
echo
echo "==> 5) Optional: idle-keepalive cron (Oracle may reclaim idle A1 instances)."
if ! crontab -l 2>/dev/null | grep -q vykronis-keepalive; then
  ( crontab -l 2>/dev/null; \
    echo "*/5 * * * * curl -s -o /dev/null http://localhost:8080/api/incidents >/dev/null 2>&1 # vykronis-keepalive" ) | crontab -
  echo "    Keepalive cron installed (GET /api/incidents every 5 min)."
fi

docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml \
  --profile apps ps --format "table {{.Name}}\t{{.Status}}"