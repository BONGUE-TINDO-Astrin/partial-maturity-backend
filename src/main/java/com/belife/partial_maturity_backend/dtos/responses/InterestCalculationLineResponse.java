package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.CalculationEventType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Explique une étape du calcul des intérêts.
 *
 * @param sequence ordre d'affichage
 * @param eventDate date de l'événement ou du cycle
 * @param eventType type d'opération
 * @param description explication lisible
 * @param cycleNumber numéro du cycle ; null hors intérêt
 * @param balanceBefore solde avant l'opération
 * @param capitalAdded capital ajouté par une maturité
 * @param annualRate taux appliqué ; null hors cycle
 * @param interestAmount intérêt produit par cette ligne
 * @param paidAmount montant payé ; zéro hors paiement
 * @param balanceAfter solde après l'opération
 */
public record InterestCalculationLineResponse(
        int sequence,
        LocalDate eventDate,
        CalculationEventType eventType,
        String description,
        Long cycleNumber,
        BigDecimal balanceBefore,
        BigDecimal capitalAdded,
        BigDecimal annualRate,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        BigDecimal balanceAfter
) {
}