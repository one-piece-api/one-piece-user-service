# ADR-0006: Per-Role Endpoints, a Dedicated User Detail View, and Last-Administrator Protection

## Context

Step 6 (UF-IDU-15 Change User Roles, UF-IDU-16 Last Administrator Protection) needed three
things `implementation-plan.md`'s original sketch left underspecified once checked against
the current codebase:

- **How a role change is expressed on the wire.** The sketch said "role update endpoint,"
  which could mean either replacing a user's entire role set in one call (matching the
  invite form's checkbox UI) or toggling individual roles. The product decision made
  explicit here: **per-role, not bulk-replace** - an ADMIN adds or removes one role at a
  time.
- **Where the editor lives in the FE.** The sketch assumed an "admin user detail view,"
  but no such view exists - Steps 3-5 all extended the single Crew Manifest list (row
  actions + `app-modal`). The product decision made explicit here: **a real, dedicated
  per-user route** (`/admin/users/:userId`), not another list-row action - deep-linkable
  and reloadable, unlike a modal driven by data already in memory.
- **How "would this leave zero ADMINs" is checked**, consistent with §2/§16's "no local
  cache/mirror of Keycloak-owned data" stance already established for Steps 3-5.

## Decision

**Two endpoints, each idempotent, role modeled as its own addressable sub-resource:**
`PUT /admin/users/{userId}/roles/{role}` (assign - a no-op if already held) and
`DELETE /admin/users/{userId}/roles/{role}` (revoke - a no-op if not held). `UserDirectoryPort`
gains matching `assignRole`/`revokeRole` methods, implemented only by
`KeycloakUserDirectoryAdapter`, following the same port/adapter boundary as every other
Step 3-5 operation.

**A new single-user endpoint, `GET /admin/users/{userId}` (`UserDirectoryPort#findUser`),
backs the new FE route.** The detail page fetches its own data by `userId` on load/refresh
rather than relying on data passed from the list - the first per-user route in this
frontend, using Angular's `withComponentInputBinding()` so the route's `:userId` arrives as
a plain signal input, not a manually injected `ActivatedRoute`.

**Last-Administrator Protection (UF-IDU-16) is a bounded Keycloak query, not a local
count.** `revokeRole` fetches at most two ADMIN-role members
(`RoleResource#getUserMembers(0, 2)`), excludes the target user, and rejects
(`LastAdministratorException`, 409) if none remain - one small, fixed-size call, never a
full role-membership listing, consistent with Step 3's "bounded regardless of user count"
principle. UF-IDU-15's more general "at least one role must remain" rule
(`LastRoleException`, 409) is enforced from the already-fetched target user's own role
list, no extra call needed.

**Check order for `revokeRole`: the ADMIN-specific rule is evaluated before the generic
one.** When the account being modified holds only the ADMIN role (the realm's bootstrap
admin, for instance), both UF-IDU-15 and UF-IDU-16 are technically satisfied at once.
Reporting `LastAdministratorException` first is more actionable than
`LastRoleException`: re-adding some other role would not fix the actual problem (this
account being the realm's last ADMIN). `AdminUserListingIntegrationTest`, run against a
real Keycloak, caught the original ordering - the seeded `luffy` account holds only ADMIN,
so `revokingTheOnlyAdministratorIsRejected` returned `USER_LAST_ROLE` instead of
`USER_LAST_ADMINISTRATOR` until the checks were reordered.

**No new audit-log column for which role changed.** `ROLE_ASSIGNED`/`ROLE_REVOKED`
(`AuditAction`) record actor + target only, exactly like every existing audit action -
`USER_INVITED` doesn't record which roles were granted either. `audit_log` stays
intentionally coarse, write-only traceability (`docs/adr/0001-audit-log-persistence.md`),
not a queryable history of role changes.

No Keycloak realm or service-account changes were needed: `manage-users` (role mutation)
and `view-users`/`query-users` (the `getUserMembers` query) were already granted to
`user-service-admin`'s service account since Steps 3-4.

## Alternatives considered

- **Bulk `PUT` with the full desired role set**, mirroring the invite form's checkboxes:
  fewer round-trips for a multi-role edit, but loses per-role idempotency and makes "which
  single role change was rejected" ambiguous when only one of several toggled roles hits
  UF-IDU-15/16. Not chosen - the per-role model keeps each mutation, and its possible
  rejection, unambiguous.
- **Reuse the existing list endpoint's in-memory data for the detail view** instead of a
  new `GET /admin/users/{userId}`: rejected - breaks direct navigation/refresh of the
  detail route, and passing a full `User` through router state is a less standard
  mechanism than a real, fetchable resource.
- **Record the changed role on the audit event**: rejected for the same reason invite
  doesn't record granted roles - would require a schema change to `audit_log` for a table
  deliberately scoped to coarse traceability, not fine-grained history.

## Consequences

- The FE gains its first per-user route and its first use of route-param signal inputs
  (`withComponentInputBinding()` added to `provideRouter` in `app.config.ts`).
- Two new `UserErrorCode` values, `LAST_ADMINISTRATOR` and `LAST_ROLE`, each mapped to 409
  by the existing `ApplicationExceptionHandler` - no handler changes needed.
- `revokeRole` always performs the bounded `getUserMembers` query when `role == ADMIN`,
  even in the common case where the cheaper "only one role held" check would also reject
  it - accepted for the ADMIN-first ordering above; the query is capped at two results
  regardless.
