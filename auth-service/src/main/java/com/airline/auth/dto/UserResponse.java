package com.airline.auth.dto;

import java.time.Instant;
import java.util.List;

/** Public view of a user. Never contains the password or its hash. */
public record UserResponse(Long id, String email, List<String> roles, boolean enabled, Instant createdAt) {
}
