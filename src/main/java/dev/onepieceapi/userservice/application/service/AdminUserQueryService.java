package dev.onepieceapi.userservice.application.service;

import dev.onepieceapi.userservice.application.port.out.UserDirectoryPort;
import dev.onepieceapi.userservice.domain.User;
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

	public Page<User> list(Pageable pageable) {
		int offset = (int) pageable.getOffset();
		List<User> content = this.userDirectoryPort.findUsers(offset, pageable.getPageSize());

		return new PageImpl<>(content, pageable, this.userDirectoryPort.countUsers());
	}

}
