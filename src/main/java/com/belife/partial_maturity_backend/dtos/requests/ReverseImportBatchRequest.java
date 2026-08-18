package com.belife.partial_maturity_backend.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande motivée d'annulation d'un chargement.
 *
 * @param reason justification de la réversion
 */
public record ReverseImportBatchRequest(

        @NotBlank(
                message =
                        "Le motif d'annulation du chargement est obligatoire."
        )
        @Size(
                max = 500,
                message =
                        "Le motif d'annulation du chargement ne doit pas dépasser 500 caractères."
        )
        String reason
) {
}