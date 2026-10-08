package com.airline.auth.controller;

import com.airline.auth.dto.ApiResult;
import com.airline.auth.dto.AuthResponse;
import com.airline.auth.dto.SigninRequest;
import com.airline.auth.dto.SignupRequest;
import com.airline.auth.dto.UserResponse;
import com.airline.auth.security.AuthenticatedUser;
import com.airline.auth.service.AuthService;
import com.airline.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/signup")
    @SecurityRequirements // public endpoint - no lock icon in Swagger
    @Operation(summary = "Create an account", description = "Password: 8-72 characters. New accounts get the CUSTOMER role.")
    public ResponseEntity<ApiResult<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully created a new user", authService.signup(request)));
    }

    @PostMapping("/signin")
    @SecurityRequirements
    @Operation(summary = "Sign in and receive a JWT",
            description = "Send the returned token as 'Authorization: Bearer <token>' on every other request.")
    public ResponseEntity<ApiResult<AuthResponse>> signin(@Valid @RequestBody SigninRequest request) {
        return ResponseEntity.ok(ApiResult.ok("Successfully signed in", authService.signin(request)));
    }

    @GetMapping("/isAuthenticated")
    @Operation(summary = "Check a token (kept from the Node API)",
            description = "200 with the user when the token is valid and the account is still active, otherwise 401.")
    public ResponseEntity<ApiResult<UserResponse>> isAuthenticated(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResult.ok("User is authenticated and token is valid",
                userService.requireActiveUser(principal.id())));
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile (with up-to-date roles)")
    public ResponseEntity<ApiResult<UserResponse>> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the user",
                userService.requireActiveUser(principal.id())));
    }

    @GetMapping("/isAdmin")
    @Operation(summary = "Is the caller an ADMIN?",
            description = "Based on the caller's token. (The Node version took an id in a GET body and was public.)")
    public ResponseEntity<ApiResult<Boolean>> isAdmin(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched whether the user is admin",
                userService.isAdmin(principal.id())));
    }
}
