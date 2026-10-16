# one-piece-user-service

Caller identity (`/api/me`), invitations and account lifecycle through the Keycloak Admin API, role/permission catalog. Setup: `README.md`. Product rules: `../docs/user-flows/application-user-identity-management.md`.

## Commands

```bash
./gradlew check > "$TMP/check.log" 2>&1; echo "exit=$?"
./gradlew format
./gradlew updateOpenApiSpec && ./scripts/generate-bruno-collection.sh
./scripts/deploy-local.sh
```

Port 8081 (`/api`); through the cluster: `http://localhost:4180/api/...`.

## Map (`dev.onepieceapi.userservice`): hexagonal (ports and adapters)

| Package | Holds |
|---|---|
| `domain` | Domain types |
| `application/service` | Use cases |
| `application/port/out` | Ports the use cases need (Keycloak, persistence) |
| `application/exception` | Error codes and exceptions |
| `adapter/in/web` | Controllers, DTOs, web mappers, `security` (permission registry, `SecuredEndpoint`) |
| `adapter/out/keycloak` | Keycloak Admin API adapter |
| `adapter/out/persistence` | JPA (audit log) |

Unlike content-service (layered), this service is hexagonal: keep new code in that shape.

## Key ideas (ADRs in `docs/adr/`)

- 0003 activation via Keycloak required actions (no custom e-mail flow); 0004 invitation expiry read from Keycloak admin events.
- 0007 permissions are Keycloak composite roles; 0009 permission-based endpoint registry; 0012 role/permission catalog management; 0013 excluded realm roles filtered from the JWT.
- The UI reads permissions, never role names.
