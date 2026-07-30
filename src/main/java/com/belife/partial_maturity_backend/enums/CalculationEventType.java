package com.belife.partial_maturity_backend.enums;

/**
 * Types d'événement pouvant apparaître dans
 * le détail d'une simulation.
 */
public enum CalculationEventType {

    /**
     * Une maturité ajoute du capital au solde.
     */
    MATURITY_ADDED,

    /**
     * Un cycle complet de douze mois applique
     * un intérêt capitalisé au solde.
     */
    INTEREST_APPLIED,

    /**
     * Un paiement valide clôture la situation
     * et remet le solde à zéro.
     */
    PAYMENT_APPLIED
}