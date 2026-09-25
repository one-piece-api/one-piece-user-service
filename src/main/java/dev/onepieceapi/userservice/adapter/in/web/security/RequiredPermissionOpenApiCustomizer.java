package dev.onepieceapi.userservice.adapter.in.web.security;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Documents each operation's required permission as the {@code x-required-permission}
 * extension, read from {@link SecuredEndpoint} - the same registry that enforces it, so
 * the published contract can never disagree with the actual authorization rules. See
 * {@code docs/adr/0014-openapi-contract-and-bruno-collection.md}.
 */
@Component
class RequiredPermissionOpenApiCustomizer implements OpenApiCustomizer {

	static final String EXTENSION = "x-required-permission";

	@Override
	public void customise(OpenAPI openApi) {
		openApi.getPaths()
			.forEach((path, pathItem) -> pathItem.readOperationsMap()
				.forEach((method, operation) -> SecuredEndpoint
					.requiredPermission(HttpMethod.valueOf(method.name()), path)
					.ifPresent(permission -> operation.addExtension(EXTENSION, permission.value()))));
	}

}
