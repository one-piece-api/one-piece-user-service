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

> **Correction (found during Step 6 QA, see `docs/adr/0006-role-update-endpoints-and-user-detail-view.md`):**
> the check above was real but insufficient - it verified the declarative User Profile's
> *reported* permissions via the Admin REST API, not what the hosted "Update Account
> Information" required-action page actually renders. In practice the invited user had no
> way to set a username there: the built-in `UPDATE_PROFILE` required action does not
> decide whether to show the username field from the User Profile permissions alone: it
> additionally gates on the realm's legacy `editUsernameAllowed` flag for that specific
> screen, independently of what `/users/profile` reports (`edit: ["admin", "user"]`
> unchanged either way - confirmed by re-querying it after the fix below, same result).
> **`editUsernameAllowed: true` was added to both `realm-onepiece.json` and the
> Testcontainers fixture `one-piece-user-service/src/test/resources/onepiece-realm.json`**,
> and confirmed fixed by walking through the actual required-action page (a temporary
> credential + `UPDATE_PROFILE` required action set on the seeded `usopp` account via
> `kcadm.sh`, reverted after) - the Username field is now present and editable. Applying
> this to the already-running local cluster required deleting and re-importing the
> `onepiece` realm (Keycloak does not update an already-imported realm on restart,
> keycloak/keycloak#14884), which also removed the one real (non-seeded) invited account
> that existed only in the live DB - re-invited afterward.
>
> **Follow-up correction (same QA pass):** the default declarative profile that grants
> `username` self-edit also leaves `email`, `firstName` and `lastName` self-editable on the
> same screen - including `email`, which UF-IDU-02's rules now explicitly forbid changing
> during activation (see `application-user-identity-management.md`), since it would let an
> invited user set an address never confirmed by UF-IDU-04. Fixed with an **explicit User
> Profile config** (`onepiece-infrastructure/scripts/configure-user-profile.sh`, a postsync
> Helmfile hook next to `configure-realm-smtp.sh`) restricting `email`'s `permissions.edit`
> to `["admin"]` only, `view` unchanged - `firstName`/`lastName` were deliberately left
> self-editable: both are `required` on this same screen and never pre-filled by
> `KeycloakUserDirectoryAdapter`'s invite flow, so locking them down would leave an invited
> user unable to complete activation at all. Not embedded in `realm-onepiece.json` itself
> (the `components` block for a custom User Profile is a known-unreliable Keycloak import
> path - keycloak/keycloak#23970, adorsys/keycloak-config-cli#979), same reasoning as the
> SMTP password already being set via a postsync hook rather than the realm JSON. Confirmed
> with the same `usopp` walkthrough: typing into Email left its value unchanged, typing into
> Username still worked.

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
