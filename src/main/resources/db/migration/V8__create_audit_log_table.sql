/*
 * BeLife Insurance
 * Journal d'audit des opérations métier sensibles.
 *
 * Principes :
 * - une ligne représente un événement métier définitif ;
 * - une ligne d'audit n'est jamais modifiée ou supprimée
 *   par l'application ;
 * - l'acteur et l'instant de l'action sont conservés
 *   explicitement ;
 * - les informations complémentaires sont stockées
 *   dans un document JSON structuré ;
 * - aucun mot de passe, JWT, hash ou secret ne doit
 *   être enregistré.
 */

CREATE TABLE partial_maturity.audit_log
(
    id BIGINT IDENTITY(1,1) NOT NULL,

    /*
     * Nature de l'action métier.
     *
     * Exemples :
     * USER_CREATED
     * FILE_IMPORTED
     * PAYMENT_CANCELLED
     */
    event_type VARCHAR(40) NOT NULL,

    /*
     * Type de ressource concernée par l'événement.
     *
     * Exemples :
     * USER
     * IMPORT_BATCH
     * PAYMENT
     */
    resource_type VARCHAR(40) NOT NULL,

    /*
     * Identifiant technique de la ressource.
     *
     * Il est stocké sous forme de chaîne afin de supporter
     * aussi bien les identifiants numériques que de futures
     * références alphanumériques.
     */
    resource_id VARCHAR(100) NOT NULL,

    /*
     * Numéro de police optionnel.
     *
     * Cette colonne facilite les recherches pour les événements
     * financiers sans devoir analyser le document JSON.
     */
    policy_number NVARCHAR(100) NULL,

    /*
     * Utilisateur authentifié ayant exécuté l'action.
     */
    actor_username NVARCHAR(100) NOT NULL,

    /*
     * Instant précis de l'événement.
     */
    occurred_at DATETIMEOFFSET(7) NOT NULL,

    /*
     * Description courte destinée à l'interface
     * d'administration.
     */
    summary NVARCHAR(500) NOT NULL,

    /*
     * Informations structurées complémentaires.
     *
     * Exemples :
     * montant du paiement ;
     * nom du fichier ;
     * rôle attribué ;
     * motif d'annulation.
     */
    detail_json NVARCHAR(MAX) NULL,

    CONSTRAINT pk_audit_log
        PRIMARY KEY (id),

    CONSTRAINT ck_audit_log_event_type
        CHECK (
            event_type IN (
                   'USER_CREATED',
                   'USER_UPDATED',
                   'USER_ACTIVATED',
                   'USER_DEACTIVATED',
                   'FILE_IMPORTED',
                   'FILE_REJECTED',
                   'PAYMENT_RECORDED',
                   'PAYMENT_CANCELLED'
                )
            ),

    CONSTRAINT ck_audit_log_resource_type
        CHECK (
            resource_type IN ('USER', 'IMPORT_BATCH', 'PAYMENT')
            ),

    CONSTRAINT ck_audit_log_resource_id
        CHECK (LEN(LTRIM(RTRIM(resource_id))) > 0),

    CONSTRAINT ck_audit_log_actor
        CHECK (LEN(LTRIM(RTRIM(actor_username))) > 0),

    CONSTRAINT ck_audit_log_summary
        CHECK (LEN(LTRIM(RTRIM(summary))) > 0),

    /*
     * SQL Server vérifie la validité du document JSON
     * lorsqu'une valeur est présente.
     */
    CONSTRAINT ck_audit_log_detail_json
        CHECK ( detail_json IS NULL OR ISJSON(detail_json) = 1)
);
GO


/*
 * Consultation générale du journal du plus récent
 * au plus ancien.
 */
CREATE INDEX ix_audit_log_occurred_at
    ON partial_maturity.audit_log(occurred_at DESC, id DESC);
GO


/*
 * Filtrage par type d'événement.
 */
CREATE INDEX ix_audit_log_event_type
    ON partial_maturity.audit_log(event_type, occurred_at DESC, id DESC);
GO


/*
 * Filtrage des événements d'un utilisateur.
 */
CREATE INDEX ix_audit_log_actor
    ON partial_maturity.audit_log(actor_username, occurred_at DESC, id DESC);
GO


/*
 * Recherche de l'historique d'une ressource précise.
 */
CREATE INDEX ix_audit_log_resource
    ON partial_maturity.audit_log(resource_type, resource_id, occurred_at DESC, id DESC);
GO


/*
 * Recherche des événements liés à une police.
 *
 * L'index filtré ignore les événements ne concernant
 * aucune police.
 */
CREATE INDEX ix_audit_log_policy
    ON partial_maturity.audit_log(policy_number, occurred_at DESC, id DESC)
    WHERE policy_number IS NOT NULL;
GO