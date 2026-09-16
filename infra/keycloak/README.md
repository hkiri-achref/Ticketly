# Keycloak — realm `ticketly`

Admin console: http://localhost:8081 — login `admin` / `admin` (dev only).

## Dev users (all passwords: `password`)

| Username | Realm role | Purpose |
|---|---|---|
| `alice` | `customer` | main test customer |
| `carol` | `customer` | second customer (ownership tests) |
| `bob` | `organizer` | creates venues/events |
| `admin` | `admin` | platform admin (realm user, distinct from the console admin) |

## One-time UI setup checklist (F-00, done by hand to learn the vocabulary)

1. **Realm**: top-left realm switcher → *Create realm* → name `ticketly`.
2. **Realm roles**: Realm roles → *Create role* → `customer`, `organizer`, `admin`.
3. **Client** `ticketly-api`: Clients → *Create client*
   - Type `OpenID Connect`, Client ID `ticketly-api`.
   - Capability config: *Client authentication* **Off** (public client),
     *Standard flow* **On** (Authorization Code — pairs with PKCE),
     *Direct access grants* **On** (**dev-only**: lets `curl` swap
     username/password for tokens; never enabled in production — see ADR 0001).
   - Advanced → *Proof Key for Code Exchange (PKCE)*: `S256`.
   - Valid redirect URIs `http://localhost*`, Web origins `+` (dev only).
4. **Users**: Users → *Create user* (`alice`, `bob`, `carol`, `admin`,
   set Email + First/Last name, *Email verified* On) → for each:
   - *Credentials* tab → Set password `password`, *Temporary* Off.
   - *Role mapping* tab → Assign role → filter "Realm roles" → assign per table above.
5. **Export** (UI export omits users, so use the CLI — run from `infra/`).
   The server must be stopped first: dev mode uses an embedded H2 file
   database, and a running server holds it locked.
   ```bash
   docker compose stop keycloak
   docker compose run --rm keycloak export \
     --dir /opt/keycloak/data/import-staging --realm ticketly --users realm_file
   docker compose start keycloak
   docker compose cp keycloak:/opt/keycloak/data/import-staging/ticketly-realm.json \
     keycloak/import/realm-export.json
   docker compose exec keycloak rm -rf /opt/keycloak/data/import-staging
   ```
6. Commit `import/realm-export.json`. From now on a fresh environment
   (`docker compose down -v && docker compose up -d`) recreates the realm
   automatically via `--import-realm`.

## Getting a token (after setup)

See `http/keycloak.http`, or:

```bash
curl -s -X POST \
  http://localhost:8081/realms/ticketly/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=ticketly-api&username=alice&password=password'
```

Decode the `access_token` at https://jwt.io — expect
`realm_access.roles` to contain `customer`, plus `sub`, `email`,
`preferred_username` (the claims the services will use, §9.1).