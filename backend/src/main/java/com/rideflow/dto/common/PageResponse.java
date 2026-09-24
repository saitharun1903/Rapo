package com.rideflow.dto.common;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

/** Stable pagination envelope, decoupled from Spring Data's internal JSON representation. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                describe(page.getSort()));
    }

    /** For content already mapped in bulk (e.g. with batched lookups), in the page's order. */
    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(List.copyOf(content), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), describe(page.getSort()));
    }

    private static String describe(Sort sort) {
        return sort.stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .collect(Collectors.joining(";"));
    }
}
