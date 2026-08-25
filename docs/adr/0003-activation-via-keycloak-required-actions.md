# ADR-0003: Activation (Username Selection) via Keycloak Required Actions

## Context

Step 5 of `docs/implementation-plan.md` (Accept Invitation, Resend, Confirm - UF-IDU-02/03/04)
needed a way for the invited user to choose a `username` (UF-IDU-02) as part of activation,
on top of what Step 4 already built (Keycloak's hosted pages handling `UPDATE_PASSWORD` and
`VERIFY_EMAIL` via `execute-actions-email`).

The plan's own wording for this step ("public activation endpoint", "public activation page")
implied a `user-service`-hosted flow. Two things about the existing architecture make that
literal reading problematic:

- **oauth2-proxy protects everything** except `/api/actuator/health`
  (`onepiece-infrastructure/oauth2-proxy/values-oauth2-proxy.yaml`, `skip-auth-route`). A
  `user-service`/frontend-hosted public activation page would require carving out a new
  unauthenticated route in the security perimeter.
- **Keycloak's action tokens are opaque to this application.** Step 4 (§16 of
  `application-user-identity-management.md`) already decided invitation tokens are handled
  entirely by Keycloak's own `execute-actions-email` mechanism - there is no Admin API to
  independently validate or consume that token from `user-service`. A custom "activation
  endpoint" would need its own token/session mechanism to know which invitation it's
  completing, reintroducing exactly the custom invitation-token handling Step 4 rejected, and
  conflicting with this project's standing rule against custom auth mechanisms when a
  standard one exists.

## Decision

Add `UPDATE_PROFILE` to the required actions set on invite and resend (alongside the existing
`UPDATE_PASSWORD`/`VERIFY_EMAIL`). The invited user chooses their `username` on Keycloak's own
hosted "Update Account Information" page - the same pattern already used for password and
email verification, on a host (`localhost:8080`) outside oauth2-proxy's perimeter entirely.

Verified empirically against the actual deployed realm (Keycloak 26.6.4, `onepiece-infrastructure/keycloak/values-keycloakx.yaml`)
rather than assumed: `GET /admin/realms/onepiece/users/profile` already reports the `username`
attribute's `permissions.edit` as `["admin", "user"]` with no custom User Profile component
defined in `realm-onepiece.json` - the declarative default already lets the account owner edit
their own username, independent of the legacy `editUsernameAllowed` realm flag (toggling it
had no effect on the returned permissions). No realm configuration change was needed for this.

Consequences for Step 5's scope:

- **No new public endpoint or page.** `user-service` gains only a `resendInvitation` port
  method / `POST /admin/users/{userId}/resend-invitation` (ADMIN-only, reusing the existing
  authenticated admin area) for UF-IDU-03.
- **UF-IDU-04 (Confirm User) needed no new work.** It was already fully satisfied by the
  `VERIFY_EMAIL` required action wired in Step 4; the plan's "if handled outside Keycloak's
  own required-action screen" condition does not apply here.
- **Accepted limitation - no activation audit record.** §13 of the user-flows document lists
  "invitation acceptance / account activation" as an event worth auditing, but with activation
  happening entirely on Keycloak's hosted pages, `user-service` receives no callback to know
  when it happens. Building one (e.g. a Keycloak Event Listener SPI) was considered and
  rejected as new infrastructure for a low-severity gap - consistent with the "accepted
  limitation" pattern already used elsewhere in the flow docs (e.g. UF-IDU-03's dual-valid-link
  behavior). The invitation itself and any resend remain audited, from Step 4 onward.

## Alternatives considered

- **Custom `user-service`-hosted activation page + endpoint** (the plan's literal wording):
  rejected - requires both a new unauthenticated route through oauth2-proxy and a
  self-invented token/session mechanism to tie the request back to a specific invitation,
  reopening a decision Step 4 deliberately closed.
- **Keycloak Event Listener SPI for activation audit**: rejected for now - a new
  infrastructure component to close a minor traceability gap with no other current need for
  it; revisit if a concrete need for activation timestamps emerges.

## Consequences

- `INVITATION_REQUIRED_ACTIONS` in `KeycloakUserDirectoryAdapter` is
  `UPDATE_PASSWORD, UPDATE_PROFILE, VERIFY_EMAIL` (Keycloak decides on-screen order, not this
  list) - used identically by both invite (Step 4) and resend (Step 5, UF-IDU-03).
- Resend (`resendInvitation`) rejects a `userId` that no longer exists
  (`UserNotFoundException`, 404) or is no longer PENDING (`InvitationNotPendingException`,
  409) - re-sending only ever makes sense for an account still awaiting activation.
- No frontend route or component was added for "activation" - the crew manifest's existing
  PENDING row gains a "Resend Invitation" action; the account itself becomes ACTIVE purely
  through Keycloak's own hosted pages, exactly as UF-IDU-02 describes.
