$ErrorActionPreference = "Stop"
$base = "http://localhost:8081"

$trace = @{
  id        = "9f2c1b4a-0001-4000-8000-000000000001"
  traceId   = "trace-checkout-42"
  serviceId = "payment-service"
  env       = "PROD"
  name      = "checkout"
  startTime = "2026-09-18T09:22:00Z"
  durationMs = 312
  status    = "OK"
  parentSpanId = $null
  attributes = @{}
} | ConvertTo-Json -Depth 4

$jfr = @{
  id         = "6d01aa3c-0002-4000-8000-000000000002"
  serviceId  = "payment-service"
  env        = "PROD"
  fileName   = "payment-2026-09-18.jfr"
  content    = "cmF3LWp0ci1kdW1teS1wYXlsb2Fk"
  recordedAt = "2026-09-18T09:22:05Z"
} | ConvertTo-Json -Depth 3

function PostIt($path, $body, $label) {
  try {
    $r = Invoke-RestMethod -Uri "$base$path" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30
    "$label -> 202 OK  $($r | ConvertTo-Json -Compress)"
  } catch {
    "$label -> ERR  $($_.Exception.Message)"
  }
}

PostIt "/api/ingest/traces" $trace "traces"
PostIt "/api/ingest/jfr"    $jfr   "jfr"

$w = "D:\Java_Pro_Projects\Latest_Pro\Obs_Autonomous_System\Ob_Autonomous_Platform\docs\local-demo-walkthrough.md"
$c = Get-Content $w
if ($c[227] -match '\[ \]') { $c[227] = $c[227] -replace '\[ \]', '[x]' }
Set-Content -LiteralPath $w -Value $c
"walkthrough Step15 marker: " + ($c[227] -replace '.*`\[','[')
