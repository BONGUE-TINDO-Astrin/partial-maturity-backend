package com.belife.partial_maturity_backend.enums;

/**
 * États possibles d'un chargement CSV.
 */
public enum ImportBatchStatus {

    /**
     * Chargement en cours de traitement.
     */
    PROCESSING,

    /**
     * Chargement accepté et maturités enregistrées.
     */
    IMPORTED,

    /**
     * Chargement rejeté sans insertion de maturité.
     */
    REJECTED,

    /**
     * Chargement précédemment importé puis annulé.
     *
     * <p>Le lot reste visible dans l'historique, mais
     * les maturités qu'il avait introduites ont été
     * retirées après validation métier.</p>
     */
    REVERSED
}