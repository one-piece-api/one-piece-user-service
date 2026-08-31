# ADR-0011: `roles:read` Permission, Rename `roles:write` to `roles:assign`

> The "roles are fixed... edited only through Keycloak itself" framing below is
> superseded by [ADR-0012](0012-role-permission-catalog-management.md): roles become
> fully dynamic, and a new `roles:manage` permission (added alongside `roles:read`/
> `roles:assign` here) gates creating/deleting roles and permissions in-app.

## Context

Two problems surfaced in ADR-0007's original permission mapping once every endpoint was
actually gated by permission (ADR-0009):

- **`GET /roles`** (the role→permission registry, `SecuredEndpoint.ROLES_LIST`) was gated
  on `Permission.USERS_READ` - reusing the "list users" permission for an endpoint whose
  actual resource is the authorization model itself, not a user. A caller who can see the
  role/permission registry isn't necessarily someone who should be able to list users, and
  vice versa; the two are unrelated capabilities that happened to share a permission only
  because no dedicated one existed yet.
- **`roles:write`** gates `PUT`/`DELETE /users/{userId}/roles/{role}` - assigning or
  revoking a role on a specific user. The name reads as "create or edit role definitions,"
  which this application doesn't do at all (roles are fixed: `ADMIN`/`REVIEWER`/`EDITOR`,
  edited only through Keycloak itself per ADR-0007). The Keycloak client-role's own
  `description` ("Assign or revoke a crew member's roles") already stated the real
  behavior correctly - only the short name was misleading.

## Decision

**Add `roles:read`, gating `GET /roles` in place of `users:read`. Rename `roles:write` to
`roles:assign`, keeping its behavior (gates both the assign and revoke endpoints)
unchanged.** `roles:assign` follows the same naming precedent `users:invite` already set
in this permission set: a specific action verb where the generic REST `write` would have
been vaguer, not a hard rule that every permission must be `resource:read`/`resource:write`.

```
ADMIN:    users:read, users:invite, roles:read, roles:assign, access:write, audit:read
REVIEWER: docs:read, docs:review
EDITOR:   docs:read, docs:write
```

Changed in lockstep, since the permission string is also the Keycloak client-role name:
`user-service`'s `Permission` enum (`ROLES_READ`, `ROLES_ASSIGN` added/renamed) and
`SecuredEndpoint` (`ROLES_LIST` now requires `ROLES_READ`); both realm fixtures -
`user-service`'s own Testcontainers-only `onepiece-realm.json` and
`onepiece-infrastructure/keycloak/realm-onepiece.json`, the shared realm backing every
real environment including local dev; `user-frontend`'s test fixture listing the mocked
ADMIN's permissions.

## Alternatives considered

- **Leave `GET /roles` on `users:read`.** No new permission to provision, but keeps two
  unrelated capabilities (list users, see the permission model) artificially coupled -
  rejected once a dedicated `Permission` enum made adding one exactly this cheap.
- **Rename `roles:write` to something more generic, e.g. `roles:manage`.** Vaguer than
  necessary given the permission gates exactly two well-defined actions (assign, revoke),
  never anything else. `roles:assign` says precisely what it does.

## Consequences

- **Provisioning a realm that already exists is not automatic.** Keycloak's
  `--import-realm` only creates entities missing from an already-existing realm; it does
  not update ones already present (`keycloak/keycloak#14884`, documented in
  `onepiece-infrastructure/scripts/apply-realm-configmap.sh`). Applying this change to a
  cluster whose "onepiece" realm already exists (any already-running local cluster, or a
  deployed remote environment) requires deleting that realm via the Keycloak Admin
  API/Console and restarting Keycloak so the next `--import-realm` recreates it from the
  updated `realm-onepiece.json` - which also deletes every account in that realm
  (seeded users, anyone invited since). A brand-new cluster's first `helmfile sync` needs
  no such step; the initial import is already complete and correct.
- `user-service`'s own tests are unaffected by that limitation: `AdminUserListingIntegrationTest`
  spins up a fresh Keycloak (Testcontainers) from `onepiece-realm.json` on every run, so
  the updated fixture takes effect immediately with no manual reset.
