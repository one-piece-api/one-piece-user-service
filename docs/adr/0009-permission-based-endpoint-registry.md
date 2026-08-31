# ADR-0009: Permission-Based Endpoint Registry, `/admin` Path Removal

> The controller/service naming this ADR deliberately left unchanged (see "left unchanged"
> below) is renamed after all by [ADR-0010](0010-rename-admin-controllers-and-services.md).

## Context

ADR-0007 introduced fine-grained permissions (`users:read`, `users:invite`, `roles:write`,
`access:write`, `audit:read`) as Keycloak composite-role members, but only ever finished
wiring one endpoint onto them: Step 17's `GET /admin/audit`, gated by
`hasAuthority("PERMISSION_audit:read")`. Every other endpoint on `AdminUserController`
stayed on the original `hasRole("ADMIN")` rule, applied blanket to the `/admin/**` path in
`SecurityConfig`. The two mechanisms coexisting was a migration left half-finished, not a
deliberate final state.

This was flagged as more than inconsistency: a path literally containing `/admin/` implies
role-based, ADMIN-only access, which stops being true the moment any other role is granted
one of these permissions (e.g. a REVIEWER granted `users:read`) - the path would then be
actively misleading about who can call it. Fixing the authorization mechanism alone (swap
`hasRole` for `hasAuthority`, keep the path) would not fix that; the path's name is the
part that lies.

A second, independent request shaped the fix: paths and permissions needed to be constants
- not string literals duplicated between a controller's `@GetMapping` and
`SecurityConfig`'s `requestMatchers(...)` - with a single, extensible place mapping every
endpoint to its required check, so the two can never silently drift apart and a new secured
endpoint is a one-line addition rather than a bespoke rule to remember.

## Decision

**`SecuredEndpoint`** (new enum, `adapter.in.web.security`) is the single registry of every
REST endpoint's HTTP method, path and authorization rule. Each constant reads its path from
**`ApiPaths`** (new, `adapter.in.web` - plain `public static final String` constants, not
enum-backed, because Java annotation attributes require compile-time constant expressions
that an enum accessor cannot provide) and its permission from **`Permission`** (new enum,
`adapter.in.web.security`, five constants matching ADR-0007's mapping exactly). Controllers
reference the same `ApiPaths` constants in their mapping annotations, so the path a
controller exposes and the path `SecurityConfig` checks are structurally the same value,
never two copies that can diverge.

`SecurityConfig.securityFilterChain` replaces its three hand-written `requestMatchers`
rules with a single call to `SecuredEndpoint.configureAll(...)` - the enum owns iterating
its own constants and applying each one's rule internally, so `SecurityConfig` stays
unaware of how the registry is built, only that it can be applied.
The `Permission` enum is a deliberately different call from ADR-0007's "no `Permission`
enum" decision: that one was about the JWT-claim/wire-format path (`MeResponse.permissions`),
where permissions are opaque pass-through strings and an enum would need a pointless
enum-to-wire-string mapping for data that only ever flows through. Here the set is closed,
only `SecurityConfig`'s authorization rules reason about it, and an enum is exactly the
right tool - compile-time-checked, exhaustive, discoverable.

**Every REST path drops its `/admin` segment**: `/admin/users` → `/users`,
`/admin/users/{userId}` → `/users/{userId}`, `/admin/roles` → `/roles`,
`/admin/users/{userId}/resend-invitation` → `/users/{userId}/resend-invitation`,
`/admin/users/{userId}/roles/{role}` → `/users/{userId}/roles/{role}`,
`/admin/users/{userId}/revoke-access` → `/users/{userId}/revoke-access`,
`/admin/users/{userId}/reactivate` → `/users/{userId}/reactivate`, `/admin/audit` →
`/audit`. The Angular frontend's routes follow the same rename for the same reason (the
browser-visible URL is just as capable of misleading a user about access as the API path
is): `/admin/users` → `/users`, `/admin/users/:userId` → `/users/:userId`, `/admin/audit`
→ `/audit`.

`AdminUserController`/`AdminAuditController` class names, the `Admin*Service` application
service names, and the frontend's `src/app/admin/` folder are **left unchanged** -
deliberately out of scope. These are internal identifiers, never seen outside the codebase,
and renaming them ripples across the application layer (and, on the frontend, every import
path) for no externally observable benefit. Their stale Javadoc claiming `/admin/**`,
ADMIN-only enforcement is corrected to describe the permission-based rule instead.

The `authorizeHttpRequests` catch-all stays `anyRequest().authenticated()`, not
`denyAll()`. Every real endpoint is now explicitly enumerated in `SecuredEndpoint` - the
catch-all's only remaining job is a defense-in-depth backstop for anything not yet added to
the registry, such as Spring Boot's internal `/error` forward (which Spring Security's
dispatcher-type handling also runs authorization on by default). `denyAll()` there risks
turning a plain 404/500 into a confusing 403 for an authenticated user hitting that edge
case, for no real security gain - nothing reaches that branch anyway once every real
endpoint is listed.

## Alternatives considered

- **Keep the `/admin` path, only swap `hasRole` for `hasAuthority`.** Fixes the
  authorization *mechanism* but not the actual complaint: the path segment itself still
  claims "ADMIN-only" even once a non-ADMIN role holds the permission. Rejected - the path
  is the part that misleads, not just the check behind it.
- **Scattered per-controller `hasAuthority(...)` calls, no shared registry.** Simpler to
  write per endpoint, but reintroduces exactly the drift risk a registry exists to prevent
  - nothing stops a controller's `@GetMapping` path and `SecurityConfig`'s matcher from
  silently diverging over time. Rejected.
- **`denyAll()` catch-all instead of `authenticated()`.** The stricter, more conventionally
  "secure by default" choice, but risks the `/error`-dispatch edge case above for no
  practical gain given every real endpoint is already enumerated. Rejected for now -
  revisit if a concrete need for a hard fail-closed default ever appears.

## Consequences

- `SecurityConfig`, both controllers, `MeController`, three backend test classes, and the
  Angular frontend's routes/nav registry/endpoint constants/mascot tips all updated to the
  new paths. Three `one-piece-e2e` specs updated to match.
- A new endpoint that is added to a controller but never added to `SecuredEndpoint` falls
  through to `anyRequest().authenticated()` - reachable by any authenticated user, not
  open to the world, but also not permission-gated. Forgetting the registry entry is a
  real, if bounded, risk the previous blanket `/admin/**` rule didn't have (anything under
  that path was automatically ADMIN-gated); the trade-off is accepted for the flexibility
  a per-endpoint registry provides.
- `AdminUserController`/`AdminAuditController`/`Admin*Service` names and the frontend's
  `admin/` folder no longer describe the actual access-control shape (permission-based, not
  role-based) - accepted as internal-only naming debt, not addressed here.
