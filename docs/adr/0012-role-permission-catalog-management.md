# ADR-0012: Role & Permission Catalog Management

## Context

ADR-0007 deliberately deferred an in-app permission editor: "no in-app 'permission
editor' screen exists in the approved UI target, so none is built speculatively," and
anticipated exactly the mechanism this ADR now builds ("A `PUT`/`DELETE
/roles/{role}/permissions/{permission}` pair... can be added later without redesign,
exactly when a concrete screen needs it"). The approved "Sunny Deck" UI target now has
that screen: create a role, delete a role, create a permission, and toggle which
permissions a role holds via a matrix. Building it faithfully means going further than
ADR-0007 anticipated in one respect: the reference lets an operator create *new* roles
by name, not just edit which permissions existing roles hold — so `RealmRole`, a fixed
three-value Java enum (`ADMIN`/`REVIEWER`/`EDITOR`), can no longer model "role" at all.

## Decision

**Roles become fully dynamic**, matching the reference exactly rather than a narrower
"permissions only, roles stay fixed" phase. `RealmRole` is deleted; every role name
becomes a plain `String`, validated at the application boundary against Keycloak's live
role list (`RoleDirectoryPort#listRoles()`) rather than by the Java type system. A new
`RoleNotFoundException` (404) replaces the enum's free "unrecognized value fails
deserialization" behavior wherever a role name is accepted (invite, assign/revoke).

**A new `RoleDirectoryPort`/`KeycloakRoleDirectoryAdapter` own the role/permission
catalog as its own resource**, separate from `UserDirectoryPort` (which owns user
identities). This mirrors the split already established between `UserController` and
`AuditController`: a different bounded concern gets its own port, adapter, and
controller rather than growing the existing one. `listRoles()` (moved from
`UserDirectoryPort`), `listPermissions()`, `createRole`/`deleteRole`,
`createPermission`, `assignPermission`/`revokePermission` all operate directly against
Keycloak - realm roles back "roles," client roles on `onepiece-proxy` back
"permissions," exactly the mechanism ADR-0007 established, just now read/written
dynamically instead of only read.

**New REST surface**, all gated by a new `Permission.ROLES_MANAGE` (`roles:manage`)
except listing, which stays on the existing `roles:read`:

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/roles` | `roles:read` | list roles + their permissions (unchanged) |
| POST | `/roles` | `roles:manage` | create a role, optional copy-from |
| DELETE | `/roles/{role}` | `roles:manage` | delete a role |
| GET | `/permissions` | `roles:manage` | list every permission, including unassigned |
| POST | `/permissions` | `roles:manage` | create a permission |
| PUT/DELETE | `/roles/{role}/permissions/{permission}` | `roles:manage` | assign/revoke |

**Two "don't lock everyone out" guards**, colocated with `KeycloakRoleDirectoryAdapter`
(the adapter that can cheaply query for them) rather than pushed up to the service —
the same precedent `KeycloakUserDirectoryAdapter#hasAnotherAdmin` already set: deleting
a role with members, or deleting/revoking `roles:manage` from the only role that holds
it, is rejected (`RoleInUseException`/`LastRoleManagerException`, both 409) rather than
silently leaving the catalog unmanageable.

**The Keycloak service account (`user-service-admin`) is granted `manage-realm`,
`manage-clients`, `view-clients`, and `query-clients`** (all `realm-management` client
roles), replacing the previous `manage-users`-only surface for this concern. Keycloak
splits realm-role and client-role management into two separate resource types with
their own permission checks - `manage-realm` alone (which has no composites of its own
in this Keycloak version, confirmed by inspecting `GET .../roles/manage-realm/composites`)
covers realm-role CRUD (`createRole`/`deleteRole`) but returns `403` on any client
lookup (`clients().findByClientId(...)`, the first call every permission operation
makes to resolve `onepiece-proxy`), a real failure this ADR's first grant attempt hit
during manual verification against the live cluster, not something anticipated upfront.
Applied to the already-running local cluster via a direct `kcadm.sh` grant
(non-destructive - the existing realm's users, roles, and every other setting are
untouched), with `realm-onepiece.json` updated to match so a future fresh import is
already correct.

Because audit events already predate this feature and always targeted a user
(`targetUserId`/`targetEmail`, `NOT NULL`), a new nullable `targetLabel` column/field is
added to `AuditEvent`/`audit_log` (Flyway `V2__audit_log_catalog_target.sql`) to carry a
role name or permission key for the five new catalog `AuditAction`s
(`ROLE_CREATED`, `ROLE_DELETED`, `PERMISSION_CREATED`, `PERMISSION_ASSIGNED_TO_ROLE`,
`PERMISSION_REVOKED_FROM_ROLE`) — cheaper and clearer than overloading `targetEmail` to
sometimes hold a non-email string.

## Alternatives considered

- **Keep roles fixed, add only permission CRUD** (a lighter phase considered before this
  ADR). Avoids the `RealmRole` removal ripple entirely, but doesn't match the approved
  reference, which explicitly supports creating new roles by name. Rejected: the user
  confirmed matching the reference exactly over a narrower phase.
- **A narrower Keycloak grant than `manage-realm` plus the three client-management
  roles.** Keycloak's `realm-management` client ships no role between "read/manage
  users" and "manage the whole realm" for realm roles, and no combined
  "manage-this-one-client" role scoped to just `onepiece-proxy` for client roles - the
  available roles are as granular as Keycloak gets. Rejected for lack of a narrower
  option; accepted as a real trade-off below rather than worked around with a custom
  authorization mechanism (this project's standing preference for standard solutions
  over custom ones).
- **Overload `targetEmail` to sometimes hold a role/permission name** instead of adding
  `targetLabel`. Avoids a migration, but conflates two different kinds of "what this
  event happened to" under one misleadingly-named field. Rejected for clarity.

## Consequences

- **The new grant set is materially broader than this service held before** -
  `manage-realm` can modify realm settings and every realm role, and `manage-clients`
  can modify every client in the realm (not just `onepiece-proxy`), not just users.
  Accepted because Keycloak doesn't expose a narrower built-in role for "manage
  roles/client-roles only" or "manage this one client only"; the alternative (a custom
  authorization shim narrowing it) would violate this project's preference for standard
  mechanisms over custom ones for a marginal blast-radius reduction that the application
  code itself doesn't rely on (it only ever calls the subset of Admin API operations
  this feature needs).
- **The `RealmRole` enum removal ripples through every existing role touchpoint**, not
  just the new screen: the invite form's role selection, the user-list role filter, and
  assign/revoke all move from a closed Java enum to a string validated against
  `RoleDirectoryPort#listRoles()` at runtime. `docs/adr/0007-permissions-as-keycloak-composite-roles.md`
  and `docs/adr/0011-roles-read-and-roles-assign-permissions.md` gain forward-pointer
  notes where they described roles as fixed or permission-editing as Keycloak-only.
- **Provisioning a realm that already exists is not automatic** (the same
  `--import-realm` limitation ADR-0011 already documented, `keycloak/keycloak#14884`) -
  the live local cluster's grant was applied directly via `kcadm.sh`, not a realm
  delete+reimport, so existing accounts were preserved; `realm-onepiece.json` stays the
  source of truth for any environment provisioned from scratch.
- `user-service`'s own tests are unaffected by that limitation: the real-Keycloak
  Testcontainers integration test spins up a fresh Keycloak from `onepiece-realm.json`
  (test-only fixture, already updated) on every run.
