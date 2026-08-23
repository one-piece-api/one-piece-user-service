# ADR-0001: Audit Log Persistence

## Context

Step 4 of `docs/implementation-plan.md` (Invite User, UF-IDU-01) is the first mutating
admin operation in this service. Per §5 of that plan, every mutating endpoint from this
step onward must emit an audit record (§13 of `application-user-identity-management.md`)
as part of its Definition of Done.

`user-service` currently has **no persistence of its own**. An earlier refactor
("move user-service to hexagonal architecture, decouple from Keycloak") deliberately
dropped the local `application_user` table: identity, roles, account status and the
admin listing are all now derived live from the validated JWT or Keycloak's Admin API
(see `ApplicationUser`, `UserAccount`, `UserDirectoryPort`) — no local copy, no drift risk.

Audit metadata (who invited whom, when) has no Keycloak equivalent: its action-token
mechanism (`execute-actions-email`) carries no bookkeeping of the inviting admin. §16 of
`application-user-identity-management.md` already calls this out: "Audit metadata ... is
still an application concern ... needs its own storage design once audit logging itself
is built."

An application PostgreSQL instance (`app-postgresql`, database `user_service`) is already
provisioned in `onepiece-infrastructure` (Phase 0.3) and wired into `user-service`'s Helm
release dependency graph (`needs: app/app-postgresql`), but has been unused since the
refactor above.

## Decision

Persist audit events in a dedicated `audit_log` table in the existing `user_service`
Postgres database, via Spring Data JPA with Flyway-managed migrations. Minimal record:
actor (`userId` + email of the acting admin, read from the validated token, never looked
up), action type, target `userId`/email, timestamp — no other fields until a concrete
need arises.

Scoped strictly to audit writes:

- One outbound port, `AuditLogPort` (application layer), one adapter, one JPA
  entity/repository — not a general-purpose persistence layer resurrection.
- No read/query endpoint. Audit logs exist purely for traceability, not to feed the UI —
  no invitation history or invite timestamp is shown to admins in this phase (explicit
  product decision).
- Does not reintroduce mirroring of Keycloak-owned data: identity/roles/status stay
  derived live, exactly as today. Audit metadata is genuinely application-owned data with
  no identity-provider equivalent, so this does not reopen the drift/sync failure mode the
  earlier refactor eliminated.

## Alternatives considered

- **Structured application log line** (SLF4J, dedicated marker/logger, no DB): zero new
  dependencies, consistent with the service's current no-persistence state. Rejected — no
  log aggregation/retention stack exists yet in the local `kind` cluster, so entries would
  survive only as long as the pod's own log buffer (lost on restart) and would not be
  queryable ("who invited whom" would mean grepping pod logs). Too weak for a traceability
  requirement.
- **External/dedicated audit service**: over-engineering at the current scale (one
  application, one mutating operation today) — a new component/repository with no
  concrete need yet, contrary to this project's guideline against introducing
  infrastructure without one.
- **Keycloak-side bookkeeping** (custom attribute or event listener): rejected — action
  tokens carry no such data, and building this into the identity provider would blur the
  boundary the recent refactor established (Keycloak owns identity, not application
  bookkeeping). Would also require a custom Keycloak SPI — heavier and less standard than
  a table in an already-provisioned application database.

## Consequences

- `user-service` reintroduces `spring-boot-starter-data-jpa`, a PostgreSQL JDBC driver,
  and Flyway — dependencies and a schema-migration tool absent since the
  persistence-dropping refactor.
- The `user-service` Helm release needs a datasource wired to `app-postgresql`'s Service
  DNS and its `one-piece-app-postgresql-credentials` secret; the `local` profile needs the
  equivalent via `kubectl port-forward`, matching the pattern already used for Keycloak.
- All other reads (identity, roles, status, the admin listing) remain fully
  Keycloak-derived, unchanged by this decision — Postgres is used exclusively for
  `audit_log`.
- Later mutating steps (6-9: role changes, revoke/reactivate, password/OTP) reuse this
  same `AuditLogPort` rather than each inventing its own mechanism.
