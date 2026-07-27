package com.belife.partial_maturity_backend.dtos.requests;

import com.belife.partial_maturity_backend.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Données modifiables d'un compte existant.
 *
 * Le nom d'utilisateur n'est pas modifiable dans le MVP,
 * car il constitue l'identifiant de connexion et d'audit.
 */
public record UpdateUserRequest(

        @NotBlank(message = "Le nom complet est obligatoire.")
        @Size(
            max = 200,
            message = "Le nom complet ne doit pas dépasser 200 caractères."
        )
        String fullName,

        @NotNull(message = "Le rôle est obligatoire.")
        UserRole role
) {
}