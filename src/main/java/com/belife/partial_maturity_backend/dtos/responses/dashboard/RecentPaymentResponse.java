package com.belife.partial_maturity_backend.dtos.responses.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paiement PAID récent affiché dans le tableau de bord.
 *
 * @param id identifiant du paiement
 * @param policyNumber numéro de police
 * @param paymentDate date métier du paiement
 * @param capitalAmount capital payé
 * @param interestAmount intérêts payés
 * @param paidAmount montant total payé
 * @param createdBy utilisateur ayant enregistré le paiement
 */
public record RecentPaymentResponse(
        Long id,
        String policyNumber,
        LocalDate paymentDate,
        BigDecimal capitalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        String createdBy
) {
}