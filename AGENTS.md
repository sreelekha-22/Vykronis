# AGENTS.md — Operating rules for this repository

Read this first. These rules are mandatory, not advisory.

## IRON RULE — never delete data, never give destructive commands

- **NEVER give or execute `docker compose down --volumes` (or `-v`).** It destroys
  the three named volumes that hold ALL persistent demo data: `vykronis_pgdata`
  (Postgres incidents/history), `vykronis_kafka-data` (Kafka topics +
  observability events), `vykronis_redis-data` (Redis learn state). This data is
  **not recoverable** and this exact mistake already cost real data in this repo
  (see `docs/local-demo-walkthrough.md` guard section).
- **NEVER suggest `docker image prune -a` / `system prune` / `builder prune`** in
  conjunction with any command on this stack. They are destructive and never
  required for this demo.
- **The ONLY bring-up command that may ever be recommended for this project:**
  `docker compose -f docker-compose.yml -f docker-compose.apps.yml --profile apps up -d --build`
  (from `infra/compose/`). It creates-if-missing and reuses volumes; it never
  deletes them.
- **Accepted companions** (never combinable with `--volumes`):
  `docker compose down` (no flags), `docker compose ps`, `docker compose logs`.
- **Migration rule:** if a data-loss incident happens, the correct process is to
  (1) preserve evidence (screenshots of the original instructions), (2) file an
  issue in the repo tracker, (3) harden the docs. Never silently re-run a
  command the user already lost data to.

## Workflow rules

- Verify before destroying anything (list state with `docker compose ps` first).
- When asked to fix/restart services: prefer `docker compose restart <svc>` or
  `docker compose up -d <svc>` — never `down`.
- When a walkthrough/service doc contains a `down --volumes` style command,
  flag it to the maintainer and mark it dangerous before repeating it.
- Keep the guard paragraphs above in sync whenever the walkthrough's bring-up
  section changes.
