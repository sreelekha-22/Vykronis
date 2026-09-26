# Vykronis demos run to a timestamped log file (no scrolling needed).
# Pre-req: mini-up.ps1 already ran; incident-service container is up.
# Logs land in:  Notes\demo-logs\demo-YYYYMMDD-HHMMSS.log
$ErrorActionPreference = "Continue"
Set-Location -LiteralPath $PSScriptRoot

$logDir = "D:\Java_Pro_Projects\Latest_Pro\Obs_Autonomous_System\Notes\demo-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$ts  = Get-Date -Format "yyyyMMdd-HHmmss"
$log = Join-Path $logDir "demo-$ts.log"

$up = docker inspect --format "{{.State.Running}}" vykronis-incident-service-1 2>$null
if ($up -ne "true") { Write-Host "incident-service not running -> run mini-up.ps1 first" -ForegroundColor Red; exit 1 }

$free = Get-CimInstance Win32_OperatingSystem
$freeBefore = [math]::Round($free.FreePhysicalMemory/1MB,1)
"# Vykronis demo run  $ts"              | Set-Content -LiteralPath $log -Encoding utf8
"# Host: $env:COMPUTERNAME"             | Add-Content -LiteralPath $log -Encoding utf8
"# FreeGB before: $freeBefore"          | Add-Content -LiteralPath $log -Encoding utf8
"# ------------------------------------------------------------" | Add-Content -LiteralPath $log -Encoding utf8

Write-Host "Logging demo to: $log" -ForegroundColor Green

docker rm -f vykprobe 2>$null | Out-Null
docker run -d --name vykprobe --network vykronis_default curlimages/curl sleep 1800 2>&1 | Add-Content -LiteralPath $log -Encoding utf8
docker cp demo-live.sh vykprobe:/tmp/demo.sh 2>&1 | Add-Content -LiteralPath $log -Encoding utf8

$started = Get-Date
docker exec vykprobe sh /tmp/demo.sh 2>&1 | Add-Content -LiteralPath $log -Encoding utf8
$elapsed = [math]::Round(((Get-Date) - $started).TotalMinutes,1)

$free = Get-CimInstance Win32_OperatingSystem
$freeAfter = [math]::Round($free.FreePhysicalMemory/1MB,1)
"# ------------------------------------------------------------" | Add-Content -LiteralPath $log -Encoding utf8
"# finished $ts  (ran $elapsed min)  FreeGB after: $freeAfter"   | Add-Content -LiteralPath $log -Encoding utf8

docker rm -f vykprobe 2>$null | Out-Null

Write-Host ""
Write-Host "Log saved:  $log" -ForegroundColor Green
Write-Host "Last lines of the demo state:" 
Get-Content -LiteralPath $log -Tail 12