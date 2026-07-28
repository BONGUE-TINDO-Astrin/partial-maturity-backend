/*
 * Aligne le type SQL Server de file_sha256 avec le mapping
 * Hibernate de la propriété Java String.
 *
 * CHAR(64) devient VARCHAR(64).
 *
 * L'index est temporairement supprimé afin d'éviter une éventuelle
 * dépendance empêchant la modification de la colonne.
 */

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ix_import_batch_file_sha256'
      AND object_id = OBJECT_ID(
          'partial_maturity.import_batch'
      )
)
BEGIN
DROP INDEX ix_import_batch_file_sha256
    ON partial_maturity.import_batch;
END;
GO


ALTER TABLE partial_maturity.import_batch
ALTER COLUMN file_sha256 VARCHAR(64) NOT NULL;
GO


CREATE INDEX ix_import_batch_file_sha256
    ON partial_maturity.import_batch(file_sha256);
GO