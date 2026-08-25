package dev.onepieceapi.userservice.application.exception;

import dev.onepieceapi.exception.DomainException;

/**
 * Raised when Keycloak's {@code execute-actions-email} call itself fails (invite,
 * UF-IDU-01, or resend, UF-IDU-03) - most commonly, in this project's dev setup, Resend's
 * own sandbox restriction rejecting any recipient other than the account's own verified
 * address until a sending domain is verified (see
 * {@code onepiece-infrastructure/docs/adr/0007-resend-only-email-delivery.md}). Modeled
 * as a {@link DomainException} (422) rather than the generic
 * {@code KeycloakCommunicationException} (500): the request itself was valid and the
 * account exists/was created, but the current email-delivery configuration cannot
 * complete it - a recognizable, occasionally-expected condition worth a clearer signal
 * than "unexpected error", not a genuine "something-is-broken" 500.
 */
public class EmailDeliveryFailedException extends DomainException {

	public EmailDeliveryFailedException(String email) {
		super(UserErrorCode.EMAIL_DELIVERY_FAILED, "Could not send the invitation email to " + email);
	}

}
