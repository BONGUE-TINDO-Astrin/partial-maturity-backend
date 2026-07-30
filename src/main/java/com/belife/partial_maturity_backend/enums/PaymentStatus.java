package com.belife.partial_maturity_backend.enums;

/**
 * États possibles d'un paiement.
 */
public enum PaymentStatus {

    /**
     * Paiement valide pris en compte dans la chronologie.
     *
     * Un paiement PAID remet la situation ouverte
     * de la police à zéro.
     */
    PAID,

    /**
     * Paiement annulé et exclu des simulations.
     *
     * L'annulation ne supprime aucune donnée.
     */
    CANCELLED
}