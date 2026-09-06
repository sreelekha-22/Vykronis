# Phase 5 — "Demo JWT" vs Keycloak (auth in Vykronis)

Vykronis has **one** identity seam and **two** ways to feed it. There is no
separate hand-rolled "demo JWT" verifier — the gateway either runs **open**
(no token: the daily dev loop) or as a **Keycloak OIDC resource server**
(real `RS256` access tokens). This page explains when to use which and how
the same policy decisions play out in both.

## 1. The identity seam

Everything is decided through the gateway's `IdentityProvider`, exposed to
services as `CurrentIdentity` and to the policy/incident path as a subject:

```
   request ─▶ AuthenticationFilter ─▶ Identity ─▶ GatewayAttributes.IDENTITY
                                       (subject, name, roles, scopes, service)
                       ▲                                 │
                       │ decided by                CurrentIdentity
                       │ (one active provider,      for downstream
                       │  exactly one bean)         role checks
        ┌──────────────┴──────────────┐
        │                             │
   AnonymousIdentityProvider      OidcJwtDecoderConfig
   (auth OFF, default)            (auth ON, Keycloak JWKS)
```

| Setting | Provider | Identity you get |
|---|---|---|
| `vykronis.auth.enabled=false` (default) | `AnonymousIdentityProvider` | `Identity.ANONYMOUS` for every caller |
| `vykronis.auth.enabled=true` | `AuthenticationFilter` + `ReactiveJwtDecoder` (JWKS) | claims mapped by `JwtIdentityMapper` |

Only one provider is ever active (bean-selected), so services always see a
well-formed `Identity` — never an auth layer they have to branch on.

## 2. Mode A — the demo loop, open (default)

`api-gateway/application.properties` ships with:

```properties
vykronis.auth.enabled=false
vykronis.auth.jwk-set-uri=http://localhost:8088/realms/vykronis/protocol/openid-connect/certs
vykronis.auth.issuer-uri=http://localhost:8088/realms/vykronis
```

Auth is **off**; the gateway accepts everything and every caller is
anonymous. This is what the demo/UI flow uses: the UI's `RemediationPanel`
sends an explicit operator subject (`DEMO_OPERATOR` = `ops` / approver), and
`POST /api/incidents/{id}/remediation|approve` maps a null/missing subject to
`OperatorActor.user("anonymous")`.

So "demo" ≠ a fake JWT — it is **no token at all**: the system just trusts
the subject the caller claims (the UI defaults to the ops approver so the
prod *Request remediation → Approve* flow works without any IdP running).

**When to use Mode A:** daily dev, the automated demo, and all unit/UI tests.
No Keycloak container, no token minting, no secrets.

## 3. Mode B — Keycloak, real OIDC (`--profile auth`)

Start Keycloak and enable the gateway resource server:

```bash
cd Ob_Autonomous_Platform

# bring up Keycloak on :8088 (realm "vykronis" auto-imports the development realm)
docker compose -f infra/compose/docker-compose.yml --profile auth up -d

# keep the base stack running too (kafka, postgres, …) so services can start
docker compose -f infra/compose/docker-compose.yml up -d

# run the gateway (or run-local.bat) with auth on:
set VYKRONIS_AUTH_ENABLED=true      # PowerShell: $env:VYKRONIS_AUTH_ENABLED="true"
.\scripts\run-local.bat
```

The realm (`infra/compose/keycloak/vykronis-realm.json`) is fixed for the
dev loop:

| Identity | Username / client | Password / secret | Realm roles |
|---|---|---|---|
| Standard user | `alice` | `vykronis-dev` | `vykronis-user` |
| Approver | `ops` | `vykronis-dev` | `vykronis-user`, `vykronis-approver` |
| Service account | `vykronis-cli` (cc grant) | `dev-service-secret` | client role `remediation-executor` |

**Mint a token and call the gateway** (HTTP API is really password / client
credentials grants — "demo JWT" by another name):

```bash
# user token (ops = approver → can approve PROD):
curl -s -X POST http://localhost:8088/realms/vykronis/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=vykronis-console&username=ops&password=vykronis-dev&grant_type=password' \
  | jq -r .access_token

# service token (vykronis-cli; preferred_username = service-account-vykronis-cli):
curl -s -X POST http://localhost:8088/realms/vykronis/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=vykronis-cli&client_secret=dev-service-secret&grant_type=client_credentials' \
  | jq -r .access_token

# use it:
curl http://localhost:8080/api/incidents -H "Authorization: Bearer $TOKEN"
```

The gateway decodes the token against the realm JWKS
(`/realms/vykronis/protocol/openid-connect/certs`, RSA-2048 generated key in
the realm), validates the issuer, and `JwtIdentityMapper` maps claims →
`Identity`:

- **User tokens** (`alice`, `ops`): roles come from `realm_access.roles`,
  `service=false`.
- **Service tokens** (`vykronis-cli` cc grant): `preferred_username` starts
  with `service-account-` → `service=true`, roles come from
  `resource_access.<azp>.roles` (client roles like `remediation-executor`).

## 4. Demo JWT vs Keycloak — the important differences

| | Mode A (default) | Mode B (`--profile auth`) |
|---|---|---|
| Token | none — anonymous, subject claims sent in request bodies | real `RS256` access token, `Bearer` header |
| Where the identity comes from | UI/API caller (delegated) | Keycloak issuer/audience/roles (trusted) |
| Auth failures | impossible (no auth concept) | 401 JSON: missing / malformed / invalid bearer |
| Identity fields | `Identity.ANONYMOUS` + body subject | full identity from JWKS-verified claims |
| Policy inputs | real subjects are declared (ops/approver) | same subjects come from realm roles |
| Infra | none | Keycloak container (`--profile auth`) |

Both modes drive the **same** env decision matrix — the matrix only reads
`PolicySubject(name, roles, service)`, which is the shape `Identity` already
carries. Switch the mode and the policy results for the fixed demo identities
stay identical:

| Caller | DEV rollback | PROD rollback |
|---|---|---|
| `alice` (human, no approver role) | `ALLOW` (auto) | `DENY` |
| `ops` (human + approver) | `ALLOW` (auto) | `REQUIRE_APPROVAL` |
| `service-account-vykronis-cli` | `DENY` | `DENY` |

Every decision is still appended to the hash-chained audit log — auth mode
does not bypass audit.

## 5. When is which used?

- **Everything local / CI / demo:** Mode A. Tests never mint real tokens
  (gateway auth tests mock `IdentityProvider` instead).
- **Validating the OIDC integration end-to-end:** Mode B. Run the compose
  `auth` profile, curl a token, call `:8080` and watch the Approve flow act
  on a JWT identity.
- **Real environments:** the same OIDC resource-server wiring points at your
  IdP's issuer/JWKS and realm client — nothing about the policy or incident
  path changes.

## Troubleshooting

- **`401 {"error":"unauthorized","message":"missing or malformed bearer token"}`**
  — auth is on and you sent no token (or a non-`Bearer` header). Mint one via
  Section 3.
- **`401 … "invalid bearer token"`** — token expired/untrusted or issuer
  mismatch; confirm `vykronis.auth.jwk-set-uri`/`issuer-uri` still point at
  `localhost:8088`, and Keycloak is healthy
  (`http://localhost:8088/realms/vykronis` returns 200).
- **Service account has no roles** — check the token's `azp`/`resource_access`
  client id matches `vykronis-cli`; the mapper reads client roles from that
  key (users' roles come from `realm_access` instead).
- **Keycloak "unhealthy"** — the container healthcheck talks to its own
  `localhost:8080` (not the host-mapped 8088); let it finish the `start-dev`
  boot (Start period 60s).
- **UI shows no Approve button in Mode B** — the browser either got no token
  (open Mode A falls back to `DEMO_OPERATOR`) or auth is on and the page's
  fetch has no `Authorization` header; add the token to the UI's
  `Authorization` header for the Keycloak E2E.