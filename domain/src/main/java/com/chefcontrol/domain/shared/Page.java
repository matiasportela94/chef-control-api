package com.chefcontrol.domain.shared;

import java.util.List;
import java.util.function.Function;

public record Page<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements
) {

    public int totalPages() {
        return pageSize == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);
    }

    public boolean isFirst() {
        return pageNumber == 0;
    }

    public boolean isLast() {
        return pageNumber >= totalPages() - 1;
    }

    public boolean hasContent() {
        return !content.isEmpty();
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), pageNumber, pageSize, totalElements);
    }

    public static <T> Page<T> empty(int pageNumber, int pageSize) {
        return new Page<>(List.of(), pageNumber, pageSize, 0);
    }

}
