package com.belife.partial_maturity_backend.services.models;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Représente une ligne valide lue depuis le fichier CSV.
 *
 * @param rowNumber numéro physique de la ligne
 *                  dans le fichier CSV
 * @param policyNumber numéro de police
 * @param clientName nom du client
 * @param maturityAmount montant de la maturité
 * @param interestEndDate date commune de fin
 *                        des intérêts de la police
 */
public record ParsedMaturityRow(
        int rowNumber,
        String policyNumber,
        String clientName,
        BigDecimal maturityAmount,
        LocalDate interestEndDate
) {
}