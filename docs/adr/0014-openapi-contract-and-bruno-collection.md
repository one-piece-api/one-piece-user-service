# ADR-0014: OpenAPI contract, Swagger UI and Bruno collection

## Context

The API had no machine-readable contract. Calling it by hand meant building requests
from scratch (a stale personal Bruno collection, borrowing the confidential
`onepiece-proxy` client's secret), and nothing flagged a contract change in review.
Wanted: a spec produced from the code, Swagger UI, and a Bruno collection that stays in
sync on its own, usable against both the local and the remote environment.

## Decision

- **Code-first spec with springdoc** (`springdoc-openapi-starter-webmvc-ui`). The
  controllers stay the single source of truth. Only annotations that improve accuracy
  are added: `@Tag` per controller, `@ParameterObject` on `Pageable`, and
  `@ResponseStatus` replacing `ResponseEntity.status(...)`/`noContent()` (same
  behaviour, but visible to springdoc).
- **Document-level metadata** (`OpenApiConfig`): Keycloak `oauth2` authorization code
  scheme derived from `issuer-uri`, and the server is the context path only (`/api`), so
  the spec doesn't depend on any host.
- **`x-required-permission`** on each operation (`RequiredPermissionOpenApiCustomizer`),
  read from `SecuredEndpoint`: the same registry that enforces the permission.
- **Error responses** come from `one-piece-exception` 0.2.0 (its ADR-0002): the
  `ProblemDetail` schema and `4XX`/`5XX` on every operation.
- **Served behind login**: `/api/v3/api-docs` and `/api/swagger-ui.html` require
  authentication (`SecuredEndpoint`) and sit behind oauth2-proxy like the SPA. "Try it out"
  reuses the browser's session. Each operation is still authorized by its own permission.
- **Committed snapshot**: `OpenApiSpecTest` fails if `openapi/openapi.yaml` differs from
  what the controllers produce, and `./gradlew updateOpenApiSpec` rewrites it. An API
  change therefore shows up as a reviewable diff.
- **Bruno collection in `bruno/`**: `scripts/generate-bruno-collection.sh` regenerates the
  request folders from the spec (`bru import openapi`, CLI version pinned).
  `opencollection.yml` (auth) and `environments/` are written by hand and never
  regenerated. Auth is authorization code + PKCE with the public `bruno` Keycloak client.
  Environments are `local` (via oauth2-proxy, `localhost:4180`) and `remote` (host from
  the git-ignored `bruno/.env`). Bearer tokens pass through oauth2-proxy thanks to
  `skip-jwt-bearer-tokens` (onepiece-infrastructure ADR-0017).
- **CI** (`api-contract` job): fails if `bruno/` is stale, and on a PR reports breaking
  changes against main's spec (oasdiff) as annotations, without failing.

## Alternatives considered

- **Contract-first (hand-written YAML + openapi-generator)**: a stricter contract, but it
  means rewriting working controllers around generated interfaces. No consumer needs
  that today.
- **`springdoc-openapi-gradle-plugin`**: boots the full application during the build, so it
  needs a real Keycloak and Postgres. The snapshot test reuses the Testcontainers setup
  that already exists.
- **Bruno's built-in OpenAPI Sync**: the free edition allows 5 syncs a month, and a sync
  overwrites the collection's auth settings (usebruno/bruno#7548). A committed, CI-checked
  collection has neither problem.
- **Swagger UI with its own OAuth2 login**: needs a client with web origins. Riding the
  existing oauth2-proxy session adds no new login flow.

## Consequences

- After an API change: `./gradlew updateOpenApiSpec`, then
  `scripts/generate-bruno-collection.sh` (needs Node.js), then commit both.
- Edits made in Bruno to generated requests are lost on the next regeneration. Durable
  customization belongs in `opencollection.yml`/`environments/`.
- Any signed-in user can read the full contract, including endpoints they can't call.
  It holds no secrets, and authorization never relied on hiding endpoints.
- Breaking changes are made visible, not blocked. Deciding whether one is acceptable is
  left to review.
