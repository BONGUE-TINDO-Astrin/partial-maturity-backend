package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.requests.ChangeUserStatusRequest;
import com.belife.partial_maturity_backend.dtos.requests.CreateUserRequest;
import com.belife.partial_maturity_backend.dtos.requests.UpdateUserRequest;
import com.belife.partial_maturity_backend.dtos.responses.UserResponse;
import com.belife.partial_maturity_backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Expose l'administration des comptes utilisateurs.
 *
 * Toutes les opérations sont réservées au rôle ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Crée un utilisateur et transmet l'identité
     * de l'administrateur au service métier.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(userService.createUser(request, authentication.getName()));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long userId,
        @Valid @RequestBody UpdateUserRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.updateUser(userId, request, authentication.getName())
        );
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> changeUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeUserStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.changeUserStatus(userId, request, authentication.getName())
        );
    }
}