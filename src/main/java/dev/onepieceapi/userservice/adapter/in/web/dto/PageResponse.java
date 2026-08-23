package dev.onepieceapi.userservice.adapter.in.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Minimal page envelope - deliberately not Spring Data's {@link Page} itself, whose
 * default JSON shape is a Spring HATEOAS/PagedModel concern this project has no other
 * need for, nor a reason to add that dependency for.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
