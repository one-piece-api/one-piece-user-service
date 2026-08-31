package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.RoleDirectoryPort;
import dev.onepieceapi.userservice.domain.PermissionDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class RoleQueryService {

	private final RoleDirectoryPort roleDirectoryPort;

	/** Backs the Step 13 role/permission registry - see {@code docs/adr/0007-*}. */
	public Map<String, List<String>> listRoles() {
		return this.roleDirectoryPort.listRoles();
	}

	/** Backs the role/permission management matrix - see {@code docs/adr/0012-*}. */
	public List<PermissionDefinition> listPermissions() {
		return this.roleDirectoryPort.listPermissions();
	}

}
