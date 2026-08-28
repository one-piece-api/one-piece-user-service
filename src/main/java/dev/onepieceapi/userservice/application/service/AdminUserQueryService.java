package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.RealmRole;
import dev.onepieceapi.userservice.domain.User;
import dev.onepieceapi.userservice.domain.UserFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AdminUserQueryService {

	private final UserDirectoryPort userDirectoryPort;

	public Page<User> list(Pageable pageable, UserFilter filter) {
		int offset = (int) pageable.getOffset();
		List<User> content = this.userDirectoryPort.findUsers(offset, pageable.getPageSize(), filter);

		return new PageImpl<>(content, pageable, this.userDirectoryPort.countUsers(filter));
	}

	/** Backs the Step 6 role-editor route - a single user, fetched directly by id. */
	public User getUser(UUID userId) {
		return this.userDirectoryPort.findUser(userId);
	}

	/** Backs the Step 13 role/permission registry - see {@code docs/adr/0007-*}. */
	public Map<RealmRole, List<String>> listRolePermissions() {
		return this.userDirectoryPort.listRolePermissions();
	}

}
