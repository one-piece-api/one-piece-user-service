package dev.onepieceapi.userservice.service;

import dev.onepieceapi.userservice.client.keycloak.KeycloakClient;
import dev.onepieceapi.userservice.controller.dto.UserSummaryResponse;
import dev.onepieceapi.userservice.mapper.UserRepresentationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AdminUserQueryService {

	private final KeycloakClient keycloakClient;

	public Page<UserSummaryResponse> list(Pageable pageable) {
		List<UserSummaryResponse> content = this.keycloakClient
			.users((int) pageable.getOffset(), pageable.getPageSize())
			.stream()
			.map(UserRepresentationMapper::toUserSummaryResponse)
			.toList();

		return new PageImpl<>(content, pageable, this.keycloakClient.count());
	}

}
