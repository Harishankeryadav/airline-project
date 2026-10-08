package com.airline.auth.dto;

import com.airline.auth.entity.RoleName;
import jakarta.validation.constraints.NotNull;

public record RoleRequest(@NotNull(message = "role is required (ADMIN, CUSTOMER or AIRLINE_BUSINESS)") RoleName role) {
}
