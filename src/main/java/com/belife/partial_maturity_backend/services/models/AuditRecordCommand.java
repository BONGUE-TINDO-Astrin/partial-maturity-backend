package com.belife.partial_maturity_backend.services.models;

import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;

import java.util.Map;

/**
 * Données nécessaires à la création d'une entrée
 * du journal d'audit.
 *
 * <p>Cette commande est interne au backend et ne constitue
 * pas un contrat API.</p>
 *
 * @param eventType type d'événement métier
 * @param resourceType type de ressource concernée
 * @param resourceId identifiant de la ressource
 * @param policyNumber numéro de police éventuel
 * @param actorUsername utilisateur ayant exécuté l'action
 * @param summary résumé lisible
 * @param details informations structurées complémentaires
 */
public record AuditRecordCommand(
        AuditEventType eventType,
        AuditResourceType resourceType,
        String resourceId,
        String policyNumber,
        String actorUsername,
        String summary,
        Map<String, Object> details
) {

    /**
     * Protège la commande contre une modification ultérieure
     * de la Map fournie par le service appelant.
     */
    public AuditRecordCommand {
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}