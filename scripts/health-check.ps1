# Vykronis Phase 0 health smoke check.
# Verifies every platform service answers UP on /actuator/health.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/health-check.ps1

param(
    [switch]$Wait
)

$services = @(
    @{ Name = "api-gateway";         Port = 8080 },
    @{ Name = "ingestion-service";   Port = 8081 },
    @{ Name = "event-service";       Port = 8082 },
    @{ Name = "correlation-engine";  Port = 8083 },
    @{ Name = "incident-service";    Port = 8084 },
    @{ Name = "agent-orchestrator";  Port = 8085 },
    @{ Name = "policy-service";      Port = 8086 },
    @{ Name = "remediation-service"; Port = 8087 }
)

$deadline = (Get-Date).AddMinutes(2)
$allUp = $true

foreach ($svc in $services) {
    $url = "http://localhost:$($svc.Port)/actuator/health"
    $up = $false
    do {
        try {
            $r = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($r.status -eq "UP") { $up = $true }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while (-not $up -and $Wait -and (Get-Date) -lt $deadline)

    if ($up) {
        Write-Host "[OK]  $($svc.Name) ($($svc.Port)) -> UP"
    } else {
        Write-Host "[FAIL] $($svc.Name) ($($svc.Port))"
        $allUp = $false
    }
}

if (-not $allUp) {
    Write-Error "Some services are not healthy."
    exit 1
}
Write-Host "All services healthy."
