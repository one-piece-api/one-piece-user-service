# ADR-0007: Permissions as Keycloak Composite/Client Roles

> Permission-based gating (this ADR's model) is completed for every endpoint, and the
> `/admin` path prefix removed, by
> [ADR-0009](0009-permission-based-endpoint-registry.md). The permission set itself below
> is refined by [ADR-0011](0011-roles-read-and-roles-assign-permissions.md): a `roles:read`
> permission is added, and `roles:write` is renamed `roles:assign`. The in-app permission
> editor this ADR deferred, and the "roles are fixed, edited only through Keycloak
> itself" framing below, are superseded by
> [ADR-0012](0012-role-permission-catalog-management.md): roles become fully dynamic and
> the `RealmRole` enum this ADR's `Map<RealmRole, Set<Permission>>` alternative
> references no longer exists. The `docs:read`/`docs:review`/`docs:write` permissions in
> the table below - always placeholders for the never-built "Documenti" feature (Step 18,
> descoped) - are removed entirely once the catalog is operator-manageable; REVIEWER and
> EDITOR keep existing with zero permissions rather than being deleted outright.

## Context

The application is evolving from "role name is the only unit of authorization" (today:
`ADMIN`/`REVIEWER`/`EDITOR` checked directly, e.g. `SecurityConfig`'s
`hasRole("ADMIN")` on `/admin/**`, `Header`'s `roles.includes('ADMIN')`) to a
**Role → N Permissions** model: a role is a named bundle of fine-grained permissions
(`users:read`, `docs:write`, ...), and the UI/API authorize on permissions, not role
names — so a new or re-scoped role changes behavior without redeploying UI/authorization
logic. This is a product requirement (source-of-scope: the approved "Sunny Deck" UI
target, `docs/implementation-plan.md` Step 13), not one this codebase's existing docs
had previously designed — `docs/user-flows/application-user-identity-management.md`
explicitly scopes role *capabilities* out ("A 'role' is referenced here only as an
attribute attached to an identity ... never in terms of what it authorizes"), deferring
them to a `authentication-and-user-management.md` that has never been written.

This decision has to fit two constraints already established and still in force:

- **No local mirroring of Keycloak-owned data.** `domain.User` carries no local
  persistence (Step 2's refactor removed the `application_user` table specifically to
  avoid a second, driftable copy of what Keycloak already owns); roles are read straight
  from the validated JWT's `realm_access.roles` claim (`ApplicationUserJwtAuthenticationConverter`).
- **`authorizationServicesEnabled: false`** on both realm clients
  (`onepiece-infrastructure/keycloak/realm-onepiece.json`) — Keycloak's fine-grained
  authorization services (resource/scope/policy objects) are not in use and turning them
  on is a materially bigger surface than this need calls for.

## Decision

**Permissions are modeled as Keycloak client roles; `ADMIN`/`REVIEWER`/`EDITOR` become
composite realm roles that include the matching permission client-roles:**

```
ADMIN:    users:read, users:invite, roles:write, access:write, audit:read
REVIEWER: docs:read, docs:review
EDITOR:   docs:read, docs:write
```

Client roles surface in the JWT under `resource_access.<clientId>.roles`, cleanly
separated from `realm_access.roles` — so the token distinguishes "which roles" from
"which permissions" by claim shape, not by a naming convention. `domain` gains a small
`Permission` enum matching the eight strings above; `ApplicationUserJwtAuthenticationConverter`
reads both claims and exposes permissions as authorities alongside the existing
`ROLE_<name>` ones. `MeResponse` gains a `permissions` field. A new read-only
`GET /admin/roles`, backed by `UserDirectoryPort#listRoles()` wrapping Keycloak's
`RolesResource#getRoleComposites()`, powers the UI's role→permission registry display.

Editing which permissions a role has happens **through Keycloak itself** (Admin Console
or its Admin API directly) for now — no in-app "permission editor" screen exists in the
approved UI target, so none is built speculatively. A `PUT`/`DELETE
/admin/roles/{role}/permissions/{permission}` pair, wrapping Keycloak's composite-role
add/remove API, can be added later without redesign, exactly when a concrete screen
needs it — following the same "no local cache, thin wrapper over the Admin API" pattern
already used for every role/user mutation since Step 3.

## Alternatives considered

- **Static in-code role→permission map** (a `Map<RealmRole, Set<Permission>>` constant in
  `user-service`, zero Keycloak/infra change). Simplest option and fully consistent with
  "zero new local persistence," but not runtime-editable at all — every permission change
  requires a code change and redeploy, even one made directly by an operator who already
  has Keycloak Admin Console access to do it the composite-role way for free. Rejected:
  it throws away capability Keycloak already provides, for no simplicity gain over the
  chosen option (both are "zero new persistence").
- **New local `permission`/`role_permission` tables in the app's own Postgres**, with
  dedicated CRUD endpoints. Most flexible data model to query and extend, and matches the
  UI mockup's "registry" framing literally — but reintroduces exactly the local mirror of
  authorization data that Step 2 deliberately eliminated, with the same drift risk between
  "what Keycloak says a user's roles are" and "what the local table says those roles can
  do." Rejected: no requirement here outweighs re-opening that closed decision.
- **Enabling Keycloak's fine-grained authorization services** (resource/scope/policy
  objects, `authorizationServicesEnabled: true`) and modeling permissions as
  scopes/policies. The most "correct" long-term fit for genuinely dynamic, per-resource
  authorization, but a materially larger configuration surface (policies, permissions,
  resource servers) than eight flat permission strings need. Rejected for now — revisit
  if a real per-resource authorization need (not just per-role) ever appears.

## Consequences

- Realm config (`onepiece-infrastructure/keycloak/realm-onepiece.json`) gains client
  roles and composite assignments — the first Keycloak change this project has made
  purely for an authorization *shape*, not a product flow. The specific client whose
  roles carry these permissions must be verified against the live realm before
  implementation (the SPA's access-token audience — provisionally `onepiece-proxy`).
- The JWT converter and every consumer of "what can this token do" now reads two claims
  (`realm_access.roles` for role names shown in the UI, `resource_access.<client>.roles`
  for permissions used to authorize) instead of one. `MeResponse`'s `roles` field is
  unchanged in shape; `permissions` is additive.
- `GET /admin/roles` is the first endpoint whose entire purpose is exposing an
  authorization *shape* rather than a user/audit record — kept read-only and
  `ADMIN`-gated like every other `/admin/**` endpoint until a real edit screen exists.
- A role's permission set can now be changed by an operator with Keycloak Admin Console
  access without touching `user-service` at all — a capability the static-map alternative
  would not have had.
