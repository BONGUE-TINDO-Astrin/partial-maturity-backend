package com.belife.partial_maturity_backend.dtos.requests;

import jakarta.validation.constraints.NotNull;

/**
 * Demande d'activation ou de désactivation d'un compte.
 */
public record ChangeUserStatusRequest(

        @NotNull(message = "Le nouveau statut est obligatoire.")
        Boolean active
) {
}