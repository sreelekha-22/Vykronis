# Run the Vykronis pod-kill chaos experiment in an isolated venv (Windows).
# Prereq: a running kind/k3d cluster with the helm chart installed and kubectl
# pointed at the vykronis demo context.
param()

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

if (-not (Test-Path -LiteralPath ".venv")) {
    python -m venv .venv
}
& ".venv\Scripts\python.exe" -m pip install -q -r requirements.txt
kubectl -n vykronis get pods
& ".venv\Scripts\chaos.exe" run experiment.json
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}