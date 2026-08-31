# ADR-0010: Rename `Admin*` Controllers and Services

## Context

ADR-0009 removed the `/admin` path segment because it misrepresented who could call an
endpoint once access moved from role-based to permission-based - but deliberately left the
`AdminUserController`/`AdminAuditController` classes and the `Admin*Service` application
services unchanged, reasoning that internal identifiers never seen outside the codebase
weren't worth the rename's ripple.

On reflection, the same reasoning ADR-0009 applied to the path applies to these names too:
"Admin" in a class name reads as "this is ADMIN-role-gated," which is exactly the claim
that stopped being true. Whether the identifier is public (a URL) or internal (a class
name) doesn't change whether it's misleading to the people reading it - it only changes
who reads it.

A second observation, independent of the first: every class in this codebase already lives
in `user-service`, a single-purpose module. `AdminUserQueryService`,
`AdminUserInvitationService`, `AdminUserRoleService` and `AdminUserAccessService` all repeat
"User" on top of a module name that already says so - redundant in exactly the way a
`UserService.User` field would be.

## Decision

**Drop `Admin` from every controller and application service name; keep `User` where it
still disambiguates a class from its siblings in the same package:**

| Old | New |
|---|---|
| `AdminUserController` | `UserController` |
| `AdminAuditController` | `AuditController` |
| `AdminUserQueryService` | `UserQueryService` |
| `AdminUserInvitationService` | `UserInvitationService` |
| `AdminUserRoleService` | `UserRoleService` |
| `AdminUserAccessService` | `UserAccessService` |
| `AdminAuditQueryService` | `AuditQueryService` |

`User` stays on every one of these, including `UserController`/`UserQueryService`,
despite the "redundant with the module name" observation above: `adapter.in.web` also
holds `AuditController` and `MeController`, and `application.service` also holds
`AuditQueryService` - dropping `User` as well would collapse `UserController`/
`UserQueryService` to `Controller`/`QueryService`, indistinguishable from their siblings.
"User" is redundant at the *module* level (the repository is already called
`user-service`) but not at the *class* level, where multiple resource types coexist in the
same package. Keeping it uniformly across the five service classes (rather than dropping
it only where an individual name reads as self-explanatory without it, e.g.
`InvitationService`) keeps the family's naming pattern predictable.

Field/parameter names following these classes (`adminUserQueryService`,
`adminUserRoleService`, etc.) are renamed to match (`userQueryService`, `userRoleService`,
...) for the same reason - a field name is exactly as much a reader-facing identifier as
the class name it's typed as.

## Alternatives considered

- **Drop `User` too, everywhere** (`Controller`, `QueryService`, `RoleService`, ...).
  Shaves the most, but `Controller`/`QueryService` specifically stop being usable names the
  moment a second resource exists in the same package - which is already true today
  (`Audit`). Rejected for the two controller/query-service pairs; considered and rejected
  for the other three services too, in favor of one consistent rule over a
  case-by-case judgment call on which individual name still reads fine without "User".
- **Leave the names as ADR-0009 decided.** Consistent with that ADR's original "internal
  identifiers, no externally observable benefit" reasoning, and the cheapest option.
  Rejected on reconsideration: the same misleading-name argument that justified the path
  rename applies here, and the rename's actual cost is a mechanical, single-session,
  IDE-safe operation (file renames plus find/replace), not the "ripples for no benefit"
  this ADR originally worried about.

## Consequences

- 7 main classes and 7 matching test classes renamed (`git mv` plus content updates);
  every constructor-injected field/parameter and Javadoc `{@link}` reference updated to
  match. No behavior change - purely a rename, verified by the full existing test suite
  passing unchanged.
- `docs/adr/0008-users-list-filters.md`'s reference to `AdminUserQueryService` updated to
  the new name with a pointer to this ADR, rather than left stale.
- The frontend's `src/app/admin/` folder and component selectors (`app-admin-user-list`,
  etc.) are **not** touched by this ADR - a separate, frontend-only naming question,
  deliberately out of scope here.
