package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.UserRole;

import java.time.Instant;

/**
 * Informations utilisateur exposées à l'administration.
 *
 * Cette réponse ne contient jamais le hash du mot de passe.
 */
public record UserResponse(
    Long id,
    String username,
    String fullName,
    UserRole role,
    boolean active,
    Instant lastLoginAt,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy
) {
}