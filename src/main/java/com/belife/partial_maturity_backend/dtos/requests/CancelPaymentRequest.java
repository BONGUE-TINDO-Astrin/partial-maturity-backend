package com.belife.partial_maturity_backend.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Données nécessaires à l'annulation d'un paiement.
 *
 * @param reason motif obligatoire de l'annulation
 */
public record CancelPaymentRequest(

        @NotBlank(message = "Le motif d'annulation est obligatoire.")
        @Size(max = 500, message = "Le motif d'annulation ne doit pas dépasser 500 caractères.")
        String reason
) {
}