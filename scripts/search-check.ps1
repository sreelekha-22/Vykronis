# Vykronis Phase 3 search smoke check.
# Verifies the search profile (OpenSearch) is up and accepts a document.
#
# RED step first (TDD): run this BEFORE `docker compose --profile search up`
# — it must FAIL because OpenSearch is not running yet. After wiring the
# compose profile it must PASS.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts/search-check.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/search-check.ps1 -Wait

param(
    [switch]$Wait
)

$healthUrl = "http://localhost:9200/_cluster/health"
$indexUrl  = "http://localhost:9200/vykronis-smoke/_doc/1"
$deadline  = (Get-Date).AddMinutes(3)
$up        = $false

# 1) OpenSearch REST API must answer with a green/yellow cluster.
do {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
        if ($null -ne $health.status -and $health.status -ne "red") { $up = $true }
    } catch {
        Start-Sleep -Seconds 3
    }
} while (-not $up -and $Wait -and (Get-Date) -lt $deadline)

if (-not $up) {
    Write-Error "OpenSearch is not up at $healthUrl. Start it with:`ndocker compose -f infra/compose/docker-compose.yml --profile search up -d"
    exit 1
}
Write-Host "[OK]  OpenSearch cluster status: $($health.status) -> $healthUrl"

# 2) OpenSearch must accept a document and return it back.
$body = '{"message":"phase3-search-smoke"}'
try {
    $created = Invoke-RestMethod -Method Put -Uri $indexUrl -ContentType "application/json" -Body $body -TimeoutSec 5
} catch {
    Write-Error "OpenSearch rejected the document: $($_.Exception.Message)"
    exit 1
}
if ($created.result -ne "created") {
    Write-Error "Expected PUT result 'created' but got '$($created.result)'"
    exit 1
}
Write-Host "[OK]  OpenSearch accepted document (index=vykronis-smoke, result=$($created.result)) -> $indexUrl"

$fetched = Invoke-RestMethod -Uri $indexUrl -TimeoutSec 5
if ($fetched._source.message -ne "phase3-search-smoke") {
    Write-Error "Round-trip failed: expected message 'phase3-search-smoke' but got '$($fetched._source.message)'"
    exit 1
}
Write-Host "[OK]  OpenSearch returned the stored document -> $($fetched._source.message)"
Write-Host "Search stack healthy."