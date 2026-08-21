-- Application-side counterpart to the bootstrap Keycloak user "luffy" seeded declaratively
-- in onepiece-infrastructure/keycloak/realm-onepiece.json. The userId here must match that
-- user's "userId" attribute exactly, so a token issued for the account resolves to this row
-- (see §2 of application-user-identity-management.md). This unblocks Step 1/2 before any
-- invite flow exists to create application users the normal way. luffy's ADMIN role is
-- assigned in the realm itself (realmRoles), not here — see V1 for why roles are not stored
-- in this table.

INSERT INTO application_user (user_id, email, status)
VALUES ('446fbe79-5cc4-458d-925d-9934334b6dcf', 'luffy@onepiece.local', 'ACTIVE');
