package com.airline.gateway.security;

import java.util.Collection;
import java.util.List;

/** Identity taken from a VERIFIED token. Roles are plain names, e.g. "ADMIN". */
public record AuthenticatedUser(Long id, String email, List<String> roles) {

    public boolean hasAnyRole(Collection<String> wanted) {
        return roles.stream().anyMatch(wanted::contains);
    }
}
