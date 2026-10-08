package com.airline.reminder.dto;

import java.util.Map;

/** Same envelope as every other service: {success, message, data, err}. Also used to decode flights-service replies. */
public record ApiResult<T>(boolean success, String message, T data, Object err) {

    public static <T> ApiResult<T> ok(String message, T data) {
        return new ApiResult<>(true, message, data, Map.of());
    }

    public static <T> ApiResult<T> fail(String message, Object err) {
        return new ApiResult<>(false, message, null, err);
    }
}
