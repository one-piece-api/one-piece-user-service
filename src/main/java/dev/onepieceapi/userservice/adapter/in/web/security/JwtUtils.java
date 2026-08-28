package dev.onepieceapi.userservice.adapter.in.web.security;

import lombok.experimental.UtilityClass;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@UtilityClass
public class JwtUtils {

	public UUID getRequiredUuidClaim(Jwt jwt, String claimName) {
		String claim = getRequiredStringClaim(jwt, claimName);
		try {
			return UUID.fromString(claim);
		}
		catch (IllegalArgumentException ex) {
			String message = "Token " + claimName + " claim is not a valid identifier";
			throw new InvalidBearerTokenException(message, ex);
		}
	}

	public String getRequiredStringClaim(Jwt jwt, String claimName) {
		String claim = jwt.getClaimAsString(claimName);

		if (claim == null) {
			throw new InvalidBearerTokenException("Token is missing the " + claimName + " claim");
		}

		return claim;
	}

	public List<String> getNestedStringListClaim(Jwt jwt, String parentClaim, String childClaim) {
		Map<String, Object> parent = jwt.getClaimAsMap(parentClaim);

		if (parent == null || !(parent.get(childClaim) instanceof List<?> values)) {
			return List.of();
		}

		return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}

	/**
	 * Reads the OIDC-standard {@code resource_access.<clientId>.roles} claim - a client's
	 * roles, one level deeper than {@link #getNestedStringListClaim}'s parent/child shape
	 * handles.
	 */
	public List<String> getResourceAccessClientRolesClaim(Jwt jwt, String clientId) {
		Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");

		if (resourceAccess == null || !(resourceAccess.get(clientId) instanceof Map<?, ?> clientAccess)
				|| !(clientAccess.get("roles") instanceof List<?> values)) {
			return List.of();
		}

		return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}

}
