package com.belife.partial_maturity_backend.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Présente une police et l'ensemble de ses maturités.
 *
 * <p>Les montants sont calculés côté backend avec BigDecimal
 * afin de préserver la précision financière.</p>
 *
 * @param policyNumber numéro de police tel qu'enregistré
 * @param maturityCount nombre total de maturités
 * @param totalMaturityAmount somme des montants de maturité
 * @param firstMaturityDate date de la première maturité
 * @param lastMaturityDate date de la dernière maturité
 * @param maturities maturités classées par rang croissant
 */
public record PolicyDetailResponse(
        String policyNumber,
        int maturityCount,
        BigDecimal totalMaturityAmount,
        LocalDate firstMaturityDate,
        LocalDate lastMaturityDate,
        List<PolicyMaturityResponse> maturities
) {

    public PolicyDetailResponse {
        maturities = List.copyOf(maturities);
    }
}