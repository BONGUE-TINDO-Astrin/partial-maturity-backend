/*
 * Supprime les colonnes ROWVERSION non nécessaires
 * sur les lots d'importation et les maturités.
 *
 * Ces données ne sont pas modifiables de manière concurrente
 * depuis l'interface du MVP. Les contraintes SQL et les
 * transactions garantissent leur cohérence.
 */

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(
        N'partial_maturity.import_batch'
    )
    AND name = N'row_version'
)
BEGIN
ALTER TABLE partial_maturity.import_batch
DROP COLUMN row_version;
END;
GO


IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(
        N'partial_maturity.policy_maturity'
    )
    AND name = N'row_version'
)
BEGIN
ALTER TABLE partial_maturity.policy_maturity
DROP COLUMN row_version;
END;
GO