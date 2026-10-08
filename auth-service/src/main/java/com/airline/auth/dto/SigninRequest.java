package com.airline.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SigninRequest(
        @NotBlank(message = "email is required") String email,
        @NotBlank(message = "password is required") String password) {
}
