package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.UserAccount;
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

	private final UserDirectoryPort userDirectoryPort;

	public Page<UserAccount> list(Pageable pageable) {
		List<UserAccount> content = this.userDirectoryPort.findUsers((int) pageable.getOffset(),
				pageable.getPageSize());

		return new PageImpl<>(content, pageable, this.userDirectoryPort.countUsers());
	}

}
