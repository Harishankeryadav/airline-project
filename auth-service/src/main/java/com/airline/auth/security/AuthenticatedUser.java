package com.airline.auth.security;

import java.util.List;

/** Identity extracted from a verified token. Roles are plain names, e.g. "ADMIN" (no ROLE_ prefix). */
public record AuthenticatedUser(Long id, String email, List<String> roles) {
}
