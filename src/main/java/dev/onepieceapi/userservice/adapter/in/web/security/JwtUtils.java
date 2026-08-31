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

	/**
	 * Walks {@code path} as successive nested-map keys from the token's top-level claims
	 * (e.g. {@code "realm_access", "roles"} for {@code realm_access.roles}, or
	 * {@code "resource_access", clientId, "roles"} for a client's
	 * {@code resource_access.<clientId>.roles}) and returns the string list found at that
	 * path, or an empty list if any step along the way is missing or not shaped as
	 * expected.
	 */
	public List<String> getNestedStringListClaim(Jwt jwt, String... path) {
		Object current = jwt.getClaims();

		for (String key : path) {
			if (!(current instanceof Map<?, ?> map)) {
				return List.of();
			}
			current = map.get(key);
		}

		if (!(current instanceof List<?> values)) {
			return List.of();
		}

		return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
	}

}
