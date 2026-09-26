# Deploying Vykronis on Oracle Cloud Always-Free (ARM)

**Decision (2026):** this is the only genuinely-free host that can run the full
stack. Important recent change — Oracle halved the Always-Free Ampere A1 allowance
to **2 OCPU / 12 GB RAM** on June 15, 2026. Older tutorials citing "4 OCPU / 24 GB"
are out of date for free accounts. This runbook targets the new **2 OCPU / 12 GB**
limit; the stack idles at ~5-6 GB using the lean memory caps.

---

## Step 1 — Create the Oracle account (~10 min, your part)

1. Go to https://signup.oraclecloud.com (a card is required for identity checks;
   free-tier resources are never charged).
2. Pick your **home region**. Avoid the busiest ones (A1 capacity sells out) —
   if you get "out of capacity", switch availability domain or retry later.
3. After sign-in, you get a 30-day US$300 trial credit FIRST, but you will use only
   Always-Free resources (those stay free forever).

## Step 2 — Create the network + instance (console, ~10 min)

1. **VCN:** Networking → Virtual Cloud Networks → *Start VCN Wizard* → *Create VCN with
   Internet Connectivity* → CIDR `10.0.0.0/16`, public subnet `10.0.0.0/24`.
2. **Instance:** Compute → Instances → Create Instance:
   - Shape: **Change shape → Speciality → VM.Standard.A1.Flex (Ampere ARM)**
   - OCPU count: **2**, Memory: **12 GB**
   - Image: **Ubuntu 24.04**
   - Boot volume: **47 GB**
   - Add your SSH public key.
3. **Security list** (how the public user reaches the app):
   - Network → Virtual Cloud Networks → your VCN → *Security Lists* → default list →
     add **Ingress rule: Source `0.0.0.0/0`, Destination port `8080`, TCP**.

## Step 3 — First SSH login

```bash
ssh -i ~/.ssh/your_key ubuntu@<PUBLIC_IP>
```

## Step 4 — Deploy (this is the whole deployment)

```bash
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/sreelekha-22/Vykronis.git
cd Vykronis/infra/compose
sudo bash deploy.sh            # installs docker, builds images, starts 13 services
```

`deploy.sh` builds the shared `vykronis/runtime:local` base, compiles all images on
ARM (20-40 min on 2 cores the first time — it hides the build log in
`/tmp/vykronis-build.log`), starts the stack, and installs a 5-minute keepalive cron.

## Step 5 — Verify

```bash
curl -s http://localhost:8080/api/incidents        # expect a JSON list (200)
docker compose -f docker-compose.yml -f docker-compose.apps.yml -f docker-compose.lean.yml --profile apps ps
```

Public URL for recruiters: **http://<PUBLIC_IP>:8080/** (dashboard served by the
gateway — no Node needed).

## Operational notes

- **Never `docker compose down -v`** — the three named volumes hold the demo data
  (same rule as local; `deploy.sh` and this repo never use `-v`).
- **Idle reclamation:** Oracle may stop Always-Free A1 instances it deems idle
  (memory >20% usage typically protects ours — the running stack holds ~5-6 GB).
  `deploy.sh` installs a `curl` keepalive every 5 min as insurance. If the instance
  gets stopped anyway: console → Compute → Instances → Start.
- **AI provider is `none`** on this box (no RAM/GPU budget for a local LLM) — the
  rule-based investigator produces hypotheses from real tool output, exactly as the
  validated local demo does. To run a real LLM later, raise `VYKRONIS_AI_PROVIDER`
  and point it at a hosted provider on a bigger machine.
- **Storage budget:** 47 GB boot volume (well inside Always-Free's 200 GB). Docker
  images (~2-3 GB) plus volumes keep it comfortable.
- Config is identical to the local run: same compose files, same contracts, same
  gateway on :8080. There is no special prod fork — the remote box just runs the
  real stack with the lean memory caps.