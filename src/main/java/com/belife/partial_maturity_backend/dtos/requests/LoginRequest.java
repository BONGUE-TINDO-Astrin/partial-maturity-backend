package com.belife.partial_maturity_backend.dtos.requests;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
        String username,

        @NotBlank(message = "Le mot de passe est obligatoire.")
        String password
) {
}
