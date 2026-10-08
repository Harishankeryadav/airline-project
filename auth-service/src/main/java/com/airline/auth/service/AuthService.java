package com.airline.auth.service;

import com.airline.auth.dto.AuthResponse;
import com.airline.auth.dto.SigninRequest;
import com.airline.auth.dto.SignupRequest;
import com.airline.auth.dto.UserResponse;
import com.airline.auth.entity.Role;
import com.airline.auth.entity.RoleName;
import com.airline.auth.entity.User;
import com.airline.auth.exception.DuplicateResourceException;
import com.airline.auth.exception.UnauthorizedException;
import com.airline.auth.repository.RoleRepository;
import com.airline.auth.repository.UserRepository;
import com.airline.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Compared against when the email is unknown, so "no such user" takes as long as "wrong password". */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** New accounts always start as CUSTOMER. Only an ADMIN can grant further roles. */
    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        Role customer = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER is missing - database migrations did not run"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password())); // fresh random salt per password
        user.getRoles().add(customer);
        return UserService.toResponse(userRepository.save(user));
    }

    /** Unknown email, wrong password and disabled account all give the same 401, so emails cannot be probed. */
    @Transactional(readOnly = true)
    public AuthResponse signin(SigninRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return new AuthResponse(jwtService.generate(user), "Bearer", jwtService.expiresInSeconds(),
                UserService.toResponse(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
