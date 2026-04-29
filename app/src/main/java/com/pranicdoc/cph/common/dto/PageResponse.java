package com.pranicdoc.cph.common.dto;

import java.util.List;

/**
 * Standard paginated response wrapper.
 * Wraps a page of results with metadata for offset-based pagination.
 *
 * @param <T> the type of content items
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    /**
     * Factory from Spring Data Page.
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
