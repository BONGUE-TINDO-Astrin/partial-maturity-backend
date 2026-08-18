package com.belife.partial_maturity_backend.enums;

/**
 * Types d'événements métier enregistrés
 * dans le journal d'audit.
 *
 * <p>Les valeurs sont persistées sous forme de chaînes
 * afin que le journal reste lisible directement
 * depuis SQL Server.</p>
 */
public enum AuditEventType {

    /**
     * Création d'un compte utilisateur.
     */
    USER_CREATED,

    /**
     * Modification du nom complet ou du rôle
     * d'un compte utilisateur.
     */
    USER_UPDATED,

    /**
     * Réactivation d'un compte utilisateur.
     */
    USER_ACTIVATED,

    /**
     * Désactivation d'un compte utilisateur.
     */
    USER_DEACTIVATED,

    /**
     * Fichier CSV accepté et traité avec succès.
     */
    FILE_IMPORTED,

    /**
     * Fichier CSV rejeté à cause d'au moins
     * une erreur bloquante.
     */
    FILE_REJECTED,

    /**
     * Réversion motivée d'un chargement précédemment
     * importé.
     */
    FILE_IMPORT_REVERSED,

    /**
     * Enregistrement d'un paiement total.
     */
    PAYMENT_RECORDED,

    /**
     * Annulation motivée d'un paiement.
     */
    PAYMENT_CANCELLED,


}