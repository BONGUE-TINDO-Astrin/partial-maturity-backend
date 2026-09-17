package com.belife.partial_maturity_backend.services.models;

/**
 * Résultat de l'analyse préalable à la réversion
 * d'un chargement.
 *
 * @param reversible indique si la réversion est actuellement possible
 * @param blockedReason raison métier lorsque la réversion est impossible
 */
public record ImportBatchReversalEligibility(
        boolean reversible,
        String blockedReason
) {

    public static ImportBatchReversalEligibility allowed() {
        return new ImportBatchReversalEligibility(true, null);
    }

    public static ImportBatchReversalEligibility blocked(String reason) {
        return new ImportBatchReversalEligibility(false, reason);
    }
}