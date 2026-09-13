package com.omnicare.platform.conversation.api;

import java.util.List;

/**
 * One page of results.
 *
 * <p>Hand-rolled rather than Spring Data's {@code Page}, whose JSON shape is
 * both unstable across versions and full of fields a client has no use for.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
