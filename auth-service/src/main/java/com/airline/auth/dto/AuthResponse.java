package com.airline.auth.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
}
