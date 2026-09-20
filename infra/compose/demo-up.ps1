<#
    Vykronis local demo bring-up + verify + (optional) full loop.
    Run from infra/compose:
        powershell -ExecutionPolicy Bypass -File .\demo-up.ps1          # bring up + verify API
        powershell -ExecutionPolicy Bypass -File .\demo-up.ps1 -UI       # also start the Next.js UI
        powershell -ExecutionPolicy Bypass -File .\demo-up.ps1 -Demo     # also drive investigate/remediation/approve on an OPEN incident
    Volumes are NEVER removed (no --volumes anywhere).
#>
[CmdletBinding()]
param(
    [switch]$UI,     # start the Next.js dev server after the stack is verified
    [switch]$Demo    # drive the autonomous loop on the first OPEN incident
)

$ErrorActionPreference = "Continue"
$COMPOSE_FILES = @("docker-compose.yml", "docker-compose.apps.yml")
$UI_DIR  = "D:\Java_Pro_Projects\Latest_Pro\Obs_Autonomous_System\Ob_Autonomous_Platform\ui\web"

function Step([string]$msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

function Wait-Running([int]$iterations = 12) {
    for ($i=0; $i -lt $iterations; $i++) {
        $s = docker inspect --format "{{.State.Health.Status}}" vykronis-kafka 2>$null
        if ($s -eq "healthy") { Write-Host "kafka healthy after ~$(($i+1)*20)s"; return $true }
        Start-Sleep -Seconds 20
    }
    Write-Host "kafka still not healthy after $($iterations*20)s - continuing anyway" -ForegroundColor Yellow
    return $false
}

Step "0) Docker engine"
$v = docker version --format "{{.Server.Version}}" 2>$null
if (-not $v) {
    Write-Host "Engine not responding. Launching Docker Desktop..." -ForegroundColor Yellow
    Start-Process "C:\Users\sai\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"
    for ($i=0; $i -lt 20; $i++) {
        $v = docker version --format "{{.Server.Version}}" 2>$null
        if ($v) { break }
        Start-Sleep -Seconds 15
    }
}
if (-not $v) { Write-Error "Docker engine did not come up." }
Write-Host "Engine OK: $v"

Step "1) Normalize state (down keeps all volumes)"
docker compose -f $COMPOSE_FILES[0] -f $COMPOSE_FILES[1] --profile apps down
Write-Host "down clean (volumes preserved)"

Step "2) Fresh bring-up with memory limits (NO --build)"
docker compose -f $COMPOSE_FILES[0] -f $COMPOSE_FILES[1] --profile apps up -d

Step "3) Wait for kafka health, then start gated services"
Wait-Running
docker compose -f $COMPOSE_FILES[0] -f $COMPOSE_FILES[1] --profile apps up -d

Step "4) Wait for gateway to bind :8080 (up to ~9 min on this box)"
$bound = $false
for ($i=0; $i -lt 30; $i++) {
    if (cmd /c "docker logs vykronis-api-gateway-1 2>nul" | Select-String -Quiet "Netty started on port") {
        $bound = $true
        Write-Host "gateway bound after ~$(($i+1)*20)s"
        break
    }
    Start-Sleep -Seconds 20
}
if (-not $bound) { Write-Host "gateway not bound yet - continue anyway, re-run -Verify" -ForegroundColor Yellow }

Step "5) Discover VM IP + verify API"
$ip = (wsl -d docker-desktop ip -4 -o addr show eth0 2>$null | Select-String "inet " | ForEach-Object { ($_ -split '\s+')[3] -replace '/.*','' } | Select-Object -First 1)
if (-not $ip) { Write-Error "Could not discover the docker-desktop VM IP" }
Write-Host "VM IP = $ip  (localhost:8080 is dead on this host - use this IP)"
docker ps --format "{{.Names}} {{.Status}}"

$list = curl.exe -s "http://$ip`:8080/api/incidents" --max-time 20
if ($list) {
    $incd = $list | ConvertFrom-Json
    Write-Host "incidents list OK (N=$($incd.Count))"
    $incd | ForEach-Object { "  - $($_.status) $($_.severity) $($_.serviceId) = $($_.incidentId)" }
} else {
    Write-Host "incidents list empty/unreachable" -ForegroundColor Yellow
}

Step "6) Verify search (evidence endpoint)"
$to  = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
curl.exe -s -o NUL -w "search status %{http_code} (%{time_total}s)`n" `
  "http://$ip`:8080/api/search/events?from=2026-09-18T16:00:00Z&to=2026-09-18T19:00:00Z&serviceId=payment-service" --max-time 30

if ($UI) {
    Step "7) Start Next.js UI against the VM IP"
    Write-Host "Run this in a SEPARATE real terminal to keep it alive:" -ForegroundColor Green
    Write-Host "  cd $UI_DIR"
    Write-Host "  `$env:VYKRONIS_API_URL = 'http://$ip`:8080'"
    Write-Host "  npm run dev -- -p 3100"
    Write-Host "then open http://localhost:3100"
    pushd $UI_DIR
    $env:VYKRONIS_API_URL = "http://$ip`:8080"
    npm run dev -- -p 3100
    popd
}

if ($Demo) {
    Step "8) Drive the autonomous loop on an OPEN incident"
    $open = $list | ConvertFrom-Json | Where-Object { $_.status -eq "OPEN" } | Select-Object -First 1
    if (-not $open) { Write-Host "No OPEN incident - start a bad deploy first or wait for DemoRunner" -ForegroundColor Yellow }
    else {
        $b = "http://$ip`:8080/api/incidents/$($open.incidentId)"
        Write-Host "Incident: $($open.incidentId) ($($open.title))"
        Write-Host "-> investigate"
        curl.exe -s -X POST "$b/investigate" --max-time 120
        Write-Host "`n-> remediation request"
        @{ strategy = "ROLLBACK"; subject = @{ name = "ops"; roles = @("vykronis-approver"); service = $false } } |
            ConvertTo-Json -Depth 4 | Set-Content -Encoding ascii "$env:TEMP\vyk\rem.json"
        curl.exe -s -X POST -H "Content-Type: application/json" -d "@$env:TEMP\vyk\rem.json" "$b/remediation" --max-time 30
        Write-Host "`n-> approve"
        curl.exe -s -X POST -H "Content-Type: application/json" -d "@$env:TEMP\vyk\rem.json" "$b/approve" --max-time 30
        Write-Host "`n-> waiting for VERIFYING -> RESOLVED (~60s)"
        Start-Sleep -Seconds 60
        curl.exe -s "$b"
    }
}

Write-Host "`nDone. Full runbook: D:\Java_Pro_Projects\Latest_Pro\Obs_Autonomous_System\Notes\SessionProgress_2026-09-18.md" -ForegroundColor Green