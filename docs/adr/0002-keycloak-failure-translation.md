# ADR-0002: Keycloak Failure Translation

## Context

`KeycloakUserDirectoryAdapter`'s own Javadoc says it is "the only place in this codebase
that talks to the Keycloak Admin API." Until now that was only true for the one failure
mode it explicitly recognized (a 409 on invite, translated to
`EmailAlreadyRegisteredException`) - every other failure (Keycloak unreachable, a
malformed response, any other `keycloak-admin-client`/JAX-RS exception) propagated
unchanged straight through `UserDirectoryPort`, into the application services, and out to
the controller. That leaks Keycloak-specific exception types past the one boundary meant
to contain them, and ties the rest of the codebase to `keycloak-admin-client` staying on
the classpath even outside the adapter.

## Decision

Every `UserDirectoryPort` method implemented by `KeycloakUserDirectoryAdapter` now catches
`RuntimeException` around its Keycloak calls and rethrows `KeycloakCommunicationException`
(a plain unchecked exception local to this adapter's package) - except
`EmailAlreadyRegisteredException`, which is already a deliberate, meaningful translation
and passes through unchanged.

`KeycloakCommunicationException` deliberately does **not** extend `one-piece-exception`'s
`ApplicationException`. That library's categories map to HTTP statuses a client can act on
(400/401/403/404/409/422); "the identity provider is unreachable or misbehaving" is
semantically a 502/503 (this is our own service failing to reach a dependency, not the
caller's fault), and the library has no such category today. Introducing one now would be
a library-wide decision for a single call site with no client that would currently behave
differently on 502/503 versus the generic 500 it gets today (no retry logic, no alerting
split on status code). `KeycloakCommunicationException` therefore falls through to the
library's generic `Exception` handler, same as any other unanticipated failure - a 500,
with a `traceId` for troubleshooting via logs.

## Alternatives considered

- **Add a new `ErrorCategory`/exception type to `one-piece-exception`** (e.g.
  `UpstreamServiceException`, 502/503): the more semantically correct status, but
  speculative today - one call site, no consumer that distinguishes it from 500 yet.
  Revisit when a second real "downstream dependency failed" case shows up, so the category
  is designed against two real needs instead of guessed from one.
- **Leave Keycloak's own exception types propagating unchanged**: rejected - the status
  quo this ADR fixes; defeats the adapter's own stated purpose and couples callers to
  `keycloak-admin-client`.

## Consequences

- No Keycloak/JAX-RS-specific exception type can cross `KeycloakUserDirectoryAdapter`
  anymore; every port method either returns normally or throws an application-meaningful
  exception (`EmailAlreadyRegisteredException` or `KeycloakCommunicationException`).
- All non-conflict Keycloak failures still surface as a generic 500 to API clients, same
  as before this change - only the internal exception type changed, not the HTTP contract.
