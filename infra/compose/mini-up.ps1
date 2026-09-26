# Vykronis MINIMAL bring-up for low-RAM hosts (terminal-only text demo).
# down -> up -> stop 4 services the demo doesn't need -> print IP + FreeGB.
# Final live set (9 containers): kafka postgres redis ingestion correlation
#   incident orchestrator policy remediation  (~4.5GB -> laptop stays usable).
# Volumes are preserved (never -v).
$ErrorActionPreference = "Continue"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "=== 0) Docker engine ===" -ForegroundColor Cyan
try { docker version --format "{{.Server.Version}}" } catch { Write-Host "no engine -> start Docker Desktop first" -ForegroundColor Red; exit 1 }

Write-Host "=== 1) down (volumes preserved) ===" -ForegroundColor Cyan
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml down 2>&1 | Out-Null

Write-Host "=== 2) up (retries once if Docker hiccups) ===" -ForegroundColor Cyan
for ($try = 1; $try -le 2; $try++) {
    docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml --profile apps up -d --no-build
    if ($LASTEXITCODE -eq 0) { break }
    Write-Host "   attempt $try failed - down + retry" -ForegroundColor Yellow
    docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml down 2>&1 | Out-Null
    Start-Sleep -Seconds 10
}
if ($LASTEXITCODE -ne 0) { Write-Host "up failed twice - check 'docker compose logs'" -ForegroundColor Red }

Write-Host "=== 3) stop the 4 services the text demo does not need ===" -ForegroundColor Cyan
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml stop api-gateway schema-registry payment-service remediation-service 2>&1 | Out-Null
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml ps --format "table {{.Name}}\t{{.State}}" 

Write-Host "=== 4) settle (90s) then memory ===" -ForegroundColor Cyan
Start-Sleep -Seconds 90
$free = Get-CimInstance Win32_OperatingSystem
$freeGB = [math]::Round($free.FreePhysicalMemory/1MB,1)
Write-Host "FreeGB = $freeGB  (expect >=2GB -> stable)" -ForegroundColor Green

Write-Host "=== 5) done ===" -ForegroundColor Cyan
Write-Host "Next: docker run -d --name vykprobe --network vykronis_default curlimages/curl sleep 1800" -ForegroundColor Green
Write-Host "      docker cp demo-live.sh vykprobe:/tmp/demo.sh" -ForegroundColor Green
Write-Host "      docker exec vykprobe sh /tmp/demo.sh" -ForegroundColor Green
Write-Host "(the demo prints everything in this terminal - no browser needed)"