# Vykronis pod-kill chaos experiment (Chaos Toolkit)

`experiment.json` kills the `event-service` pod in the `vykronis` namespace and
asserts the platform self-heals:

* **Steady state** — both deployments come back `Available` after the blast
  (event-service via the Deployment's Recreate strategy + readiness probe;
  agent-orchestrator untouched, because it is the deployment the
  `KubernetesExecutor` runs `rollout restart`/`undo` against during incident
  remediation).
* **Method** — `terminate_pods` with `rand: true` deletes one random
  event-service pod matched by `vykronis.io/service=event-service`.

This tests the *infrastructure-level* recovery underneath the platform's own
agentic self-healing (remediation-service's KubernetesExecutor rolls out
restarts/undos when an incident is approved). Run it, then drive an incident to
`REMEDIATING` (see `docs/phase6-demo.md` / `docs/phase7-runbook.md`) to show
both layers working together.

## Prerequisites

* A running kind/k3d cluster with the Vykronis chart installed (`helm install
  vykronis ./infra/helm/vykronis -n vykronis --create-namespace`).
  Provision with `infra/terraform-kind` if you want it declaratively.
* `kubectl` config pointing at the demo context (`kind-vykronis`).
* Python 3 (the only local dependency; chaos + the k8s driver install into a
  `.venv` here).

## Run

```bash
./tools/chaos/run.sh                 # or: .\tools\chaos\run.ps1
```

or directly:

```bash
python -m venv .venv
.venv/bin/pip install -r tools/chaos/requirements.txt
cd tools/chaos && ../../.venv/Scripts/chaos run experiment.json   # windows
```

Watch the pod roll:

```bash
kubectl -n vykronis get pods -w -l vykronis.io/service=event-service
```

## Expected outcome

* `chaos run` exits 0: steady state re-established within `duration: 180`.
* event-service pod is replaced within seconds (the Recreate strategy destroys
  and rebuilds the single-replica Deployment), Ready 1/1 afterwards.
* Any incident already in flight is unaffected (verification window/live
  `obs.alerts` consumption is on incident-service, not event-service), so the
  remediation demo still completes.

CI runs a JSON parse (`python -m json.tool`) of the experiment on every push
(see `.github/workflows/ci.yml`, `extensions` job) so the file can't rot.