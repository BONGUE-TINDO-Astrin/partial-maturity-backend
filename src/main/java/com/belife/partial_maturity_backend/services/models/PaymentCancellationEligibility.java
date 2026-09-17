package com.belife.partial_maturity_backend.services.models;

/**
 * Résultat de l'analyse de l'annulabilité
 * d'un paiement.
 *
 * @param cancellable indique si l'annulation est possible
 * @param blockedReason raison métier du blocage
 */
public record PaymentCancellationEligibility(
        boolean cancellable,
        String blockedReason
) {

    public static PaymentCancellationEligibility allowed() {
        return new PaymentCancellationEligibility(true, null);
    }

    public static PaymentCancellationEligibility blocked(String reason) {
        return new PaymentCancellationEligibility(false, reason);
    }
}