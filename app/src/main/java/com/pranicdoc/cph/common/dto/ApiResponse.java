package com.pranicdoc.cph.common.dto;

/**
 * Generic API response wrapper for endpoints that don't return domain data.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, "Success", data);
    }
}
