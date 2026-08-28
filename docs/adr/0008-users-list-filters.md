# ADR-0008: Users List Filters Trade a Cap for Correctness, Not Full Keycloak-Native Pagination

## Context

Step 15 adds search (`q`), role and status filters to `GET /admin/users`. The unfiltered
listing (Step 3) is a single, fully Keycloak-paginated call
(`GET /users?first=&max=` + `GET /users/count`) - cost independent of realm size, per
`docs/adr` precedent and `application-user-identity-management.md` §16's "no local
cache/mirror" stance.

No single Keycloak Admin API endpoint covers every filter combination this needs:

- **Role** membership has its own endpoint (`GET /roles/{role}/users`), which does not
  accept a free-text search or a status filter.
- **Free-text search** (`GET /users?search=`) does not accept a role or status filter,
  and matches broader fields (first/last name) than this app exposes.
- **Status** (`ACTIVE`/`PENDING`/`INVITATION_EXPIRED`/`DISABLED`) has no server-side
  filter at all beyond Keycloak's own `enabled` flag - `PENDING` vs `ACTIVE` vs
  `INVITATION_EXPIRED` is derived in `KeycloakUserMapper`/`isInvitationExpired` from
  `requiredActions` and the admin-events log, exactly like the unfiltered listing already
  does per user.

## Decision

**A non-empty filter resolves a capped candidate batch and paginates/counts in memory;
an empty filter keeps the original fully-native-paginated path unchanged.**
`KeycloakUserDirectoryAdapter#loadFilterCandidates` picks whichever single Keycloak call
narrows the most (role membership, then free-text search, else every user up to
`FILTER_CANDIDATE_CAP` = 500), resolves each candidate the same way the unfiltered path
does (concurrent per-user role/status resolution), then applies every filter field
in-app - including the one already used to narrow, which is redundant but keeps the
method correct regardless of which branch ran, and correctly combines two filters
neither native endpoint supports together (e.g. role + search).

The cap is a deliberate, explicit trade-off: for a realm past ~500 users, a filtered
query could miss matches beyond the cap. This project's realm is a handful of seeded
users; the cap is sized for that reality, not as a general solution. If this ever needs
to scale, the fix is a real search index (Keycloak's own, or a projection this
application owns), not a bigger cap.

## Alternatives considered

- **Reject filter combinations no single endpoint supports** (e.g. role + search
  together) - simpler, but a worse UI: a user typing into the search box while a role
  filter is already active would see it silently do nothing or error, not narrow further.
- **Add a local read-model of users for filtering** (e.g. a Postgres projection kept in
  sync via Keycloak webhooks/events) - would give proper server-side pagination at any
  scale, but reopens exactly the "second copy of Keycloak-owned data" drift risk
  Step 2's refactor deliberately eliminated, for a scale problem this project doesn't have.

## Consequences

- `UserDirectoryPort#findUsers`/`#countUsers` gained a `UserFilter` parameter
  (`query`/`role`/`status`, all optional) - a breaking signature change for the port,
  updated at its one implementation (`KeycloakUserDirectoryAdapter`) and one caller
  (`AdminUserQueryService`).
- A filtered query costs one narrowing call plus up to `FILTER_CANDIDATE_CAP` per-user
  role/status resolutions (same concurrent pattern as the unfiltered path), then two
  in-memory passes (filter, paginate) - more expensive than the unfiltered path, bounded
  by the cap rather than by realm size.
- `GET /admin/users` gains three optional query params (`q`, `role`, `status`); omitting
  all three is byte-for-byte the same request/response shape as before this step.
