/*
 * BeLife Insurance
 * Réversion contrôlée des chargements CSV.
 *
 * Principes :
 * - le lot d'import reste conservé dans l'historique ;
 * - son statut passe de IMPORTED à REVERSED ;
 * - les maturités introduites par le lot peuvent être
 *   retirées après validation métier ;
 * - l'utilisateur, la date et le motif de la réversion
 *   sont conservés ;
 * - l'opération est enregistrée dans le journal d'audit.
 */


/*
 * Informations de réversion du chargement.
 */
ALTER TABLE partial_maturity.import_batch
    ADD
        reversed_at DATETIMEOFFSET(7) NULL,
    reversed_by NVARCHAR(100) NULL,
    reversal_reason NVARCHAR(500) NULL;
GO


/*
 * Remplace la contrainte de statut afin d'autoriser
 * le nouveau statut REVERSED.
 */
IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_import_batch_status'
      AND parent_object_id = OBJECT_ID(
          N'partial_maturity.import_batch'
      )
)
BEGIN
ALTER TABLE partial_maturity.import_batch
DROP CONSTRAINT ck_import_batch_status;
END;
GO


ALTER TABLE partial_maturity.import_batch
    ADD CONSTRAINT ck_import_batch_status
        CHECK (
            status_code IN (
                            'PROCESSING',
                            'IMPORTED',
                            'REJECTED',
                            'REVERSED'
                )
            );
GO


/*
 * Cohérence des informations de réversion :
 *
 * - un lot REVERSED doit posséder les trois informations ;
 * - les autres statuts ne doivent posséder aucune
 *   information de réversion.
 */
ALTER TABLE partial_maturity.import_batch
    ADD CONSTRAINT ck_import_batch_reversal_data
        CHECK (
            (
                status_code = 'REVERSED'
                    AND reversed_at IS NOT NULL
                    AND reversed_by IS NOT NULL
                    AND reversal_reason IS NOT NULL
                    AND LEN(
                                LTRIM(
                                        RTRIM(reversal_reason)
                                )
                        ) > 0
                )
                OR
            (
                status_code <> 'REVERSED'
                    AND reversed_at IS NULL
                    AND reversed_by IS NULL
                    AND reversal_reason IS NULL
                )
            );
GO


/*
 * Remplace la contrainte des événements d'audit
 * afin d'autoriser FILE_IMPORT_REVERSED.
 */
IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_audit_log_event_type'
      AND parent_object_id = OBJECT_ID(
          N'partial_maturity.audit_log'
      )
)
BEGIN
ALTER TABLE partial_maturity.audit_log
DROP CONSTRAINT ck_audit_log_event_type;
END;
GO


ALTER TABLE partial_maturity.audit_log
    ADD CONSTRAINT ck_audit_log_event_type
        CHECK (
            event_type IN (
                           'USER_CREATED',
                           'USER_UPDATED',
                           'USER_ACTIVATED',
                           'USER_DEACTIVATED',
                           'FILE_IMPORTED',
                           'FILE_REJECTED',
                           'FILE_IMPORT_REVERSED',
                           'PAYMENT_RECORDED',
                           'PAYMENT_CANCELLED'
                )
            );
GO