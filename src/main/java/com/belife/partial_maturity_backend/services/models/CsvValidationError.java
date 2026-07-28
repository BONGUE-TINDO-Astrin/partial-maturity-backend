package com.belife.partial_maturity_backend.services.models;

/**
 * Décrit une erreur détectée dans le CSV.
 *
 * @param rowNumber numéro de ligne ; 0 pour une erreur globale
 * @param column colonne concernée, si applicable
 * @param code code technique stable
 * @param message message compréhensible par l'administrateur
 */
public record CsvValidationError(
    int rowNumber,
    String column,
    String code,
    String message
) {
}