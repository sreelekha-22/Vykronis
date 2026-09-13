#!/usr/bin/env bash
# Run the Vykronis pod-kill chaos experiment in an isolated venv.
# Prereq: a running kind/k3d cluster with the helm chart installed and kubectl
# pointed at the vykronis demo context.
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -d .venv ]; then
  python3 -m venv .venv
fi
.venv/bin/pip install -q -r requirements.txt

kubectl -n vykronis get pods
.venv/bin/chaos run experiment.json