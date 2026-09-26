# Vykronis lean bring-up for low-RAM hosts (single-command).
# down -> up 11 services -> stop the 2 unneeded -> wait gateway -> print IP + FreeGB.
# Volumes are preserved (never -v).
$ErrorActionPreference = "Continue"
Set-Location -LiteralPath $PSScriptRoot

Write-Host "=== 0) Docker engine ===" -ForegroundColor Cyan
try { docker version --format "{{.Server.Version}}" } catch { Write-Host "no engine -> start Docker Desktop first" -ForegroundColor Red; exit 1 }

Write-Host "=== 1) down (volumes preserved) ===" -ForegroundColor Cyan
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml down 2>&1 | Out-Null

Write-Host "=== 2) lean up (11 services) ===" -ForegroundColor Cyan
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml --profile apps up -d --no-build
if ($LASTEXITCODE -ne 0) { Write-Host "up failed" -ForegroundColor Red }

Write-Host "=== 3) stop unneeded extras ===" -ForegroundColor Cyan
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml stop schema-registry payment-service 2>&1 | Out-Null

Write-Host "=== 4) wait for gateway :8080 (up to ~9 min) ===" -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(9)
$bound = 0
do {
    $bound = (docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml logs api-gateway 2>&1 | Select-String "Netty started on port" | Measure-Object).Count
    if ($bound -ge 1) { break }
    Start-Sleep -Seconds 10
} while ((Get-Date) -lt $deadline)
if ($bound -lt 1) { Write-Host "gateway did not bind in time - check 'docker compose logs api-gateway'" -ForegroundColor Red }

Write-Host "=== 5) VM IP + memory ===" -ForegroundColor Cyan
$raw = wsl -d docker-desktop ip -4 -o addr show eth0
$ip = ""
foreach ($line in $raw) { if ($line -match 'inet\s+(\d+\.\d+\.\d+\.\d+)') { $ip = $matches[1]; break } }
if (-not $ip) { $ip = "UNKNOWN" }
Write-Host "VM IP = $ip"
$free = Get-CimInstance Win32_OperatingSystem
$freeGB = [math]::Round($free.FreePhysicalMemory/1MB,1)
Write-Host "FreeGB = $freeGB  (expect ~2GB, stable)"
Write-Host "Dashboard: http://$ip`:8080/" -ForegroundColor Green
Write-Host "Done."