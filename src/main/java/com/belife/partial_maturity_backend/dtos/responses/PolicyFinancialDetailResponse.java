package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Regroupe les informations nécessaires à la consultation
 * détaillée de la situation financière d'une police.
 *
 * <p>La simulation représente la situation courante.
 * L'historique contient uniquement les paiements encore
 * valides. Les chronologies figées sont chargées à la
 * demande depuis le détail de chaque paiement.</p>
 */
public record PolicyFinancialDetailResponse(
        String policyNumber,
        int maturityCount,
        BigDecimal totalMaturityAmount,
        LocalDate firstMaturityDate,
        LocalDate lastMaturityDate,
        LocalDate interestEndDate,
        boolean interestAccrualClosed,
        BigDecimal paidInterestAmount,
        BigDecimal totalPaidAmount,
        BigDecimal totalGeneratedInterestAmount,
        List<PolicyMaturityResponse> maturities,
        List<PolicyPaymentHistoryResponse> payments,
        InterestSimulationResponse simulation
) {

    public PolicyFinancialDetailResponse {
        maturities = List.copyOf(maturities);
        payments = List.copyOf(payments);
    }
}