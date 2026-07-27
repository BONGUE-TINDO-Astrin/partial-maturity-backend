package com.belife.partial_maturity_backend.dtos.requests;

import com.belife.partial_maturity_backend.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Données nécessaires à la création d'un compte utilisateur.
 */
public record CreateUserRequest(

    @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
    @Size(
        min = 3,
        max = 100,
        message = "Le nom d'utilisateur doit contenir entre 3 et 100 caractères."
    )
    @Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "Le nom d'utilisateur contient des caractères non autorisés."
    )
    String username,

    @NotBlank(message = "Le nom complet est obligatoire.")
    @Size(
        max = 200,
        message = "Le nom complet ne doit pas dépasser 200 caractères."
    )
    String fullName,

    @NotNull(message = "Le rôle est obligatoire.")
    UserRole role,

    @NotBlank(message = "Le mot de passe est obligatoire.")
    @Size(
        min = 12,
        max = 200,
        message = "Le mot de passe doit contenir au moins 12 caractères."
    )
    String password
) {
}