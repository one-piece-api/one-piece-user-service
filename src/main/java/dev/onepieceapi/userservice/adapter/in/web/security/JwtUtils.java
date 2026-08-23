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

}
