package com.airline.auth.service;

import com.airline.auth.dto.UserResponse;
import com.airline.auth.entity.Role;
import com.airline.auth.entity.RoleName;
import com.airline.auth.entity.User;
import com.airline.auth.exception.BadRequestException;
import com.airline.auth.exception.ResourceNotFoundException;
import com.airline.auth.exception.UnauthorizedException;
import com.airline.auth.repository.RoleRepository;
import com.airline.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll(Sort.by("id")).stream().map(UserService::toResponse).toList();
    }

    /** Used for "who am I" checks: the token may be valid, but the account must still exist and be enabled. */
    @Transactional(readOnly = true)
    public UserResponse requireActiveUser(Long id) {
        User user = userRepository.findById(id)
                .filter(User::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("This account no longer exists or has been disabled"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(Long id) {
        return userRepository.findById(id).filter(User::isEnabled).map(u -> u.hasRole(RoleName.ADMIN)).orElse(false);
    }

    @Transactional
    public UserResponse grantRole(Long userId, RoleName roleName) {
        User user = findOrThrow(userId);
        user.getRoles().add(findRole(roleName));
        return toResponse(user);
    }

    @Transactional
    public UserResponse revokeRole(Long userId, RoleName roleName) {
        User user = findOrThrow(userId);
        if (!user.hasRole(roleName)) {
            throw new BadRequestException("User " + userId + " does not have role " + roleName);
        }
        if (roleName == RoleName.ADMIN && userRepository.countByRolesName(RoleName.ADMIN) <= 1) {
            throw new BadRequestException("Cannot remove the last ADMIN");
        }
        user.getRoles().removeIf(role -> role.getName() == roleName);
        return toResponse(user);
    }

    /** Creates the user as ADMIN (+CUSTOMER), or promotes an existing account. Used only at startup. */
    @Transactional
    public void ensureAdmin(String email, String rawPassword) {
        String normalized = email.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null) {
            if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            user = new User();
            user.setEmail(normalized);
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            user.getRoles().add(findRole(RoleName.CUSTOMER));
        }
        user.getRoles().add(findRole(RoleName.ADMIN));
        userRepository.save(user);
    }

    public static UserResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new UserResponse(user.getId(), user.getEmail(), roles, user.isEnabled(), user.getCreatedAt());
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }

    private Role findRole(RoleName name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Role " + name + " is missing - database migrations did not run"));
    }
}
