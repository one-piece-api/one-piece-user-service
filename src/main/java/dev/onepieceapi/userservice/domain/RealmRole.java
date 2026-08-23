package dev.onepieceapi.userservice.domain;

/**
 * The realm roles an ADMIN can assign when inviting a user (UF-IDU-01), matching the
 * realm's own role set (`onepiece-infrastructure/keycloak/realm-onepiece.json`). The
 * admin listing (Step 3) reads roles as plain strings straight off Keycloak - any value
 * it returns is displayed as-is - but assigning a role is a write the application
 * controls, so it is validated against this fixed set instead of accepting an arbitrary
 * string.
 */
public enum RealmRole {

	ADMIN, REVIEWER, EDITOR

}
