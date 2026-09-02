# ADR-0013: Filter Keycloak's built-in realm roles out of the JWT-derived user too

## Context

Keycloak auto-assigns its own `default-roles-<realm>` composite (and, until
recently, `offline_access`/`uma_authorization` inside it - see
`onepiece-infrastructure`'s ADR-0012) to every account. `keycloak.admin.excluded-realm-roles`
already filtered these out of the Admin-API-backed listings
(`KeycloakUserDirectoryAdapter`, `KeycloakRoleDirectoryAdapter`, UF-IDU-17),
but `ApplicationUserJwtAuthenticationConverter` - which resolves the calling
user's own roles straight from the token's `realm_access.roles` claim for
every authenticated request, including `/me` - read that claim verbatim.
Keycloak includes `default-roles-<realm>` in that claim for every account
regardless of realm configuration, so it surfaced in `/me`'s response even
after the realm-side fix.

## Decision

`excludedRealmRoles` moves out of `KeycloakAdminProperties` (Admin REST
client connection details: server/realm/credentials) into its own
`KeycloakRoleProperties` (`keycloak.roles.excluded-realm-roles`), and
`ApplicationUserJwtAuthenticationConverter` filters `realm_access.roles`
through it before building both the resolved `User` and the Spring
Security authorities - the same list and the same filtering rule the Admin
API adapters already apply, now shared instead of duplicated.

`KeycloakRoleProperties` lives in the top-level `config` package (alongside
`ClockConfig`) rather than `adapter.out.keycloak.config`: the JWT converter
is an inbound adapter with no other reason to depend on outbound-adapter
config, and the excluded-role list is genuinely shared knowledge about
Keycloak, not an Admin API concern.

Four `@WebMvcTest` slices (`MeControllerTest`, `RoleControllerTest`,
`AuditControllerTest`, `UserControllerTest`) now `@Import
KeycloakRoleConfig` - Spring's `@WebMvcTest` auto-includes any `Converter`
bean regardless of slice, so `ApplicationUserJwtAuthenticationConverter`
was already being instantiated in these contexts; it just had no
constructor dependency to satisfy before.

## Alternatives considered

- **Read `keycloak.admin.excluded-realm-roles` directly via `@Value` in the
  converter**: works without a shared properties type or the `@WebMvcTest`
  `@Import` changes (property sources, unlike config classes, are not
  slice-filtered), but splits one concept across two different Spring
  binding mechanisms (a `@ConfigurationProperties` record and a raw
  `@Value`) for no real gain over the extra `@Import` lines.
- **Leave `excludedRealmRoles` on `KeycloakAdminProperties` and inject that
  whole record into the converter**: smallest diff, but couples an inbound
  adapter to outbound-adapter connection config (server URL, client
  secret) it has no other reason to know about.

## Consequences

- `/me` (and any future code reading `User#roles()` or a `ROLE_*`
  authority from the security context) never sees a Keycloak-internal role
  name - one config list, one filtering rule, applied everywhere a realm
  role list is surfaced.
- A future realm role meant to be excluded application-wide needs adding to
  `keycloak.roles.excluded-realm-roles` only once.
