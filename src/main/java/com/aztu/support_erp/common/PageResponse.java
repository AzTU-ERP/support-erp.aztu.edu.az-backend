package com.aztu.support_erp.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Lightweight pagination wrapper so we never serialize the JPA Page directly. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
    public static <S, T> PageResponse<T> from(Page<S> page, List<T> mapped) {
        return new PageResponse<>(mapped, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
