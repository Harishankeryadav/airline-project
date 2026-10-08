package com.airline.booking.security;

import java.util.List;

/** Identity taken from a VERIFIED token (never from request headers or the request body). */
public record AuthenticatedUser(Long id, String email, List<String> roles) {

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
