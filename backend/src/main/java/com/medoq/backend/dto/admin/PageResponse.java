package com.medoq.backend.dto.admin;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic pagination wrapper returned by all admin list endpoints.
 */
public record PageResponse<T>(
        List<T>  content,
        int      page,
        int      size,
        long     totalElements,
        int      totalPages,
        boolean  last
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(
            p.getContent(),
            p.getNumber(),
            p.getSize(),
            p.getTotalElements(),
            p.getTotalPages(),
            p.isLast()
        );
    }
}
