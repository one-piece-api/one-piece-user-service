# ADR-0004: Invitation Expiry Gating for Resend

## Context

Step 5's resend endpoint (UF-IDU-03) originally allowed resending for any PENDING user,
at any time. Reviewing that behavior surfaced a real consequence of Keycloak's
`execute-actions-email` action-token mechanism (already an accepted limitation since Step
4, §16 of `application-user-identity-management.md`): each invite/resend issues an
independent, single-use token with its own validity window, and Keycloak never
invalidates a previously issued one. Reading `ExecuteActionsActionTokenHandler`'s own
source confirms the concrete effect: redeeming *any* still-valid token unconditionally
re-adds `UPDATE_PASSWORD`/`UPDATE_PROFILE`/`VERIFY_EMAIL` to the session regardless of the
account's current state - so a stale but unexpired link, clicked after the account is
already ACTIVE, can reset that account's password. Every resend, until now, added another
such link to whatever mailbox already held one, multiplying this exposure rather than
replacing anything.

The fix is to only allow resending once the current link has actually gone stale - never
while a previously issued one is still redeemable. That requires knowing, for a given
PENDING account, when its invitation was last (re)sent and how long that link is valid
for.

## Decision

**Token lifespan is owned entirely by this application, not Keycloak's realm-wide
default.** `KeycloakInvitationProperties.tokenLifespan` (`keycloak.invitation.token-lifespan`,
`PT12H`) is passed explicitly as the `lifespan` parameter on every
`execute-actions-email` call (invite and resend alike), rather than left to the realm's
own `actionTokenGeneratedByAdminLifespan`. This is the only place that value is defined -
nothing to fetch from Keycloak or keep in sync with a separate realm setting.

**"When was it last sent" is read from Keycloak's own admin-events log, not tracked by
this application.** The realm now has `adminEventsEnabled: true` (`onepiece-infrastructure`),
which makes Keycloak itself record every `execute-actions-email` call as an `ACTION`
event with `resourcePath` `users/{userId}/execute-actions-email` and its own timestamp -
verified directly against `UserResource.java`'s admin-event logging call. `user-service`
queries this (`RealmResource.getAdminEvents(...)`, filtered to that exact `resourcePath`,
`direction=desc`, `max=1`) to get the most recent send time for a given user, with no
storage of its own.

**A new conceptual status, `AccountStatus.INVITATION_EXPIRED`, refines PENDING.** Derived
in `KeycloakUserDirectoryAdapter` as `now - lastSentAt > tokenLifespan` for PENDING
accounts only (ACTIVE/DISABLED users never trigger the admin-events lookup at all). No
recorded event at all (e.g. admin events enabled after the account was invited) is treated
as *not* expired rather than guessed. `resendInvitation` now requires
`INVITATION_EXPIRED` specifically - a merely-PENDING account with its link still valid is
rejected the same way an already-ACTIVE one is (`InvitationNotResendableException`, 409),
which replaced `InvitationNotPendingException` for that reason: "not pending" no longer
describes every rejection case.

## Alternatives considered

- **Read the "last sent" timestamp from this application's own `audit_log`** (which
  already records `USER_INVITED`/`INVITATION_RESENT` with timestamps, Step 4): rejected -
  `audit_log` was deliberately designed write-only, "not a UI/product data source"
  (`docs/adr/0001-audit-log-persistence.md`); repurposing it to gate a live authorization
  decision crosses that boundary for no real benefit over reading the fact from Keycloak
  directly, which also keeps a single source of truth instead of two (Keycloak's own
  action-token send, and our copy of when we think we sent it).
- **Shorten the realm's token lifespan instead of gating resend**: reduces the exposure
  window per link but does not address the actual multiplication problem - repeated
  resends still stack up several independently valid links within whatever window is
  chosen. Complementary at best, not a substitute; not pursued as a standalone fix.
- **No changes (status quo)**: the previously accepted limitation's stated scope ("both
  links remain valid... until used or expired") undersold the actual behavior (a stale
  link can silently reset an already-active account's password), which is enough of a
  correctness gap to fix rather than re-document.

## Consequences

- Every PENDING row in the admin listing now costs one additional, targeted Keycloak
  Admin API call (bounded per page, run concurrently alongside the existing per-row role
  fetch from Step 3 - not per total user count).
- Admin Events are now enabled realm-wide, not scoped to just this one call - any future
  admin-console/Admin-API mutation on this realm is also logged. Retention
  (`eventsExpiration: 604800`, 7 days) bounds growth; details are off
  (`adminEventsDetailsEnabled: false`) since only the timestamp and resource path are
  needed.
- The `user-service-admin` service account needs one more `realm-management` client role,
  `view-events` (alongside the existing `view-realm`/`view-users`/`query-users`/
  `manage-users` from Steps 3-4) - without it, `GET /admin/realms/{realm}/admin-events`
  returns 403 and every PENDING row in the listing fails with a 500. Added to both
  `onepiece-infrastructure/keycloak/realm-onepiece.json` and this repo's own
  `src/test/resources/onepiece-realm.json` fixture (caught by
  `AdminUserListingIntegrationTest`, which runs against a real Testcontainers Keycloak).
- `UF-IDU-03`'s UI/API contract changes: resending is only offered/accepted once an
  invitation is expired, not for every PENDING row - the FE only shows "Resend Invitation"
  for `INVITATION_EXPIRED` rows.
