package com.airline.flights.dto;

import java.util.Map;

/** Uniform response envelope - same shape as the original Node API: {success, message, data, err}. */
public record ApiResult<T>(boolean success, String message, T data, Object err) {

    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(true, message, data, Map.of());
    }

    public static <T> ApiResult<T> fail(String message, Object err) {
        return new ApiResult<>(false, message, null, err);
    }
}
