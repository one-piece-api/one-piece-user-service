# ADR-0005: Username as a JWT Claim and the UI's Primary Identifier

## Context

§2 of `application-user-identity-management.md` already describes `username` as "a
unique, user-facing handle chosen by the user during invitation acceptance" (UF-IDU-02),
and ADR-0003 already has the invited user choose it on Keycloak's own hosted "Update
Account Information" page. What was missing: the application never read it back. The
domain `User` record and `ApplicationUserJwtAuthenticationConverter` resolved identity
from only `sub`, `email` and `realm_access.roles`; the UI displayed email everywhere a
human-facing identifier was needed (navbar, profile card, Crew Manifest).

Verified against the deployed realm rather than assumed: `realm-onepiece.json` defines
its own `profile` client scope (overriding Keycloak's built-in one, to add the `full name`
mapper) with no mapper for `preferred_username` - unlike a stock realm, this application's
tokens did not carry it. Confirmed empirically that a token issued by this realm has no
`preferred_username` claim before adding the mapper below.

## Decision

**A `username` protocol mapper added to the realm's custom `profile` client scope**
(`onepiece-infrastructure/keycloak/realm-onepiece.json`), mapping the account's
`username` attribute to the standard `preferred_username` OIDC claim - the same pattern
already used for the `email` mapper on the `email` scope. No new client, no new scope: the
existing `defaultClientScopes` (`onepiece-proxy`) already include `profile`.

**`User` gains a `username` field**, resolved the same two ways `email` already is (see
`User`'s own javadoc): from the JWT's `preferred_username` claim for the caller's own
identity (`ApplicationUserJwtAuthenticationConverter`), or from Keycloak's Admin API
(`UserRepresentation.getUsername()`, via `KeycloakUserMapper`) for someone else's, e.g. the
admin listing (UF-IDU-17). For a still-`PENDING`/`INVITATION_EXPIRED` account, this is
Keycloak's own reality, not a special case the application invents: `username` there is
still the email placeholder `KeycloakUserDirectoryAdapter#createUnactivatedUser` sets at
invite time (§2's own rule - the real, user-chosen value doesn't exist until activation),
so an admin identifying a not-yet-activated row still sees the address they invited.

**`username` becomes the UI's primary human-facing identifier, replacing email**: the
navbar, the "who am I" profile card, and the Crew Manifest table now show it. `email`
remains in every response (`MeResponse`, `UserSummaryResponse`) - still needed to address
someone at invite time, still shown as the row's data for anyone whose displayed
"username" is actually still their email placeholder - just no longer the UI's primary
label for an activated user.

## Alternatives considered

- **A custom Keycloak user attribute instead of the native `username`**: rejected - the
  native `username` field is already exactly this concept (unique per realm, user-editable
  per ADR-0003's verified `UserProfile` permissions), and Keycloak enforces its uniqueness
  natively. A parallel custom attribute would duplicate that uniqueness constraint instead
  of reusing it.
- **Falling back to a placeholder string (e.g. "Pending activation") for accounts whose
  username is still the email placeholder**: rejected - the placeholder *is* the invited
  email address, which is exactly what an admin needs to identify that row; masking it
  would remove real information for no benefit.

## Consequences

- `preferred_username` joins `sub`, `email` and `realm_access.roles` as claims this
  application depends on being present on every token - `ApplicationUserJwtAuthenticationConverter`
  now rejects a token missing it the same way it already does for a missing `email`.
- `MeResponse`/`UserSummaryResponse` are both additive, non-breaking changes for any
  existing consumer reading `email`/`roles`/`status` - `username` is a new field, nothing
  removed.
- e2e/frontend assertions keyed on a seeded user's literal email text in a UI element now
  need the corresponding seeded `username` instead, wherever that element switched to
  displaying `username` (unaffected for a still-PENDING row, where the two values are
  still identical).
