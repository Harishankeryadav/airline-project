package com.airline.auth.controller;

import com.airline.auth.dto.ApiResult;
import com.airline.auth.dto.RoleRequest;
import com.airline.auth.dto.UserResponse;
import com.airline.auth.entity.RoleName;
import com.airline.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User administration (ADMIN)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all users")
    public ResponseEntity<ApiResult<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the users", userService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public ResponseEntity<ApiResult<UserResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the user", userService.getById(id)));
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "Grant a role", description = "Takes effect in the user's next token (next sign-in).")
    public ResponseEntity<ApiResult<UserResponse>> grantRole(@PathVariable Long id,
                                                             @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Role granted", userService.grantRole(id, request.role())));
    }

    @DeleteMapping("/{id}/roles/{role}")
    @Operation(summary = "Revoke a role", description = "The last ADMIN cannot be removed.")
    public ResponseEntity<ApiResult<UserResponse>> revokeRole(@PathVariable Long id, @PathVariable RoleName role) {
        return ResponseEntity.ok(ApiResult.ok("Role revoked", userService.revokeRole(id, role)));
    }
}
