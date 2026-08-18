package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Représente un paiement dans la liste paginée.
 *
 * <p>Les lignes justificatives ne sont pas incluses dans
 * cette réponse. Elles sont chargées uniquement lorsque
 * l'utilisateur ouvre le détail du paiement.</p>
 *
 * @param id identifiant du paiement
 * @param policyNumber numéro de police
 * @param paymentDate date métier du paiement
 * @param calculationDate date métier du calcul
 * @param annualRate taux annuel appliqué
 * @param capitalAmount capital inclus dans le paiement
 * @param interestAmount intérêts inclus dans le paiement
 * @param paidAmount montant total payé
 * @param completedCycles nombre de cycles annuels appliqués
 * @param status statut actuel du paiement
 * @param cancelledAt date technique d'annulation
 * @param createdAt date technique de création
 * @param createdBy utilisateur ayant enregistré le paiement
 */
public record PaymentSummaryResponse(
        Long id,
        String policyNumber,
        LocalDate paymentDate,
        LocalDate calculationDate,
        BigDecimal annualRate,
        BigDecimal capitalAmount,
        BigDecimal interestAmount,
        BigDecimal paidAmount,
        long completedCycles,
        PaymentStatus status,
        Instant cancelledAt,
        Instant createdAt,
        String createdBy
) {
}