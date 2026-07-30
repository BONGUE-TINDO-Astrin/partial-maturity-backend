package com.belife.partial_maturity_backend.services.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Événement chronologique traité par le moteur.
 *
 * <p>Ce modèle interne permet au moteur de traiter
 * uniformément les maturités et les futurs paiements.</p>
 *
 * @param eventId identifiant de l'entité source
 * @param eventDate date métier de l'événement
 * @param eventKind nature interne de l'événement
 * @param rank rang de maturité, ou zéro pour un paiement
 * @param label libellé explicatif
 * @param amount montant ajouté ou payé
 */
public record CalculationEvent(
        Long eventId,
        LocalDate eventDate,
        EventKind eventKind,
        int rank,
        String label,
        BigDecimal amount
) {

    /**
     * Nature interne utilisée pour le tri.
     *
     * MATURITY précède PAYMENT lorsque les dates
     * sont identiques.
     */
    public enum EventKind {
        MATURITY,
        PAYMENT
    }
}