# Vykronis Phase 3 observability smoke check.
# Verifies the observability stack (OTel Collector, Prometheus, Grafana) is up
# and exposing its metrics/UI endpoints.
#
# RED step first (TDD): run this BEFORE `docker compose --profile observability up`
# — it must FAIL because the services are not running yet. After wiring the
# compose profile it must PASS.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/observability-check.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/observability-check.ps1 -Wait

param(
    [switch]$Wait
)

$targets = @(
    @{ Name = "otel-collector internal"; Url = "http://localhost:8888/metrics" },
    @{ Name = "otel-collector metrics";  Url = "http://localhost:8889/metrics" },
    @{ Name = "prometheus";             Url = "http://localhost:9090/metrics" },
    @{ Name = "prometheus targets";     Url = "http://localhost:9090/api/v1/targets" },
    @{ Name = "grafana";                Url = "http://localhost:3000/api/health" }
)

$deadline = (Get-Date).AddMinutes(2)
$allUp = $true

foreach ($t in $targets) {
    $up = $false
    do {
        try {
            $r = Invoke-WebRequest -Uri $t.Url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) { $up = $true }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while (-not $up -and $Wait -and (Get-Date) -lt $deadline)

    if ($up) {
        Write-Host "[OK]  $($t.Name) -> $($t.Url)"
    } else {
        Write-Host "[FAIL] $($t.Name) -> $($t.Url)"
        $allUp = $false
    }
}

if (-not $allUp) {
    Write-Error "Observability stack not fully up. Start it with:`ndocker compose -f infra/compose/docker-compose.yml --profile observability up -d"
    exit 1
}
Write-Host "Observability stack healthy."
