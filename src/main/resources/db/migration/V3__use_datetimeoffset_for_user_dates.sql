/*
 * Aligne les colonnes temporelles de app_user avec le type Java Instant.
 *
 * Java Instant est mappé par Hibernate vers DATETIMEOFFSET sur SQL Server.
 */

-- Suppression des contraintes DEFAULT actuelles
DECLARE @ConstraintName NVARCHAR(200);
DECLARE @Sql NVARCHAR(MAX);


/*
 * created_at
 */
SELECT @ConstraintName = dc.name
FROM sys.default_constraints dc
         INNER JOIN sys.columns c
                    ON c.default_object_id = dc.object_id
         INNER JOIN sys.tables t
                    ON t.object_id = c.object_id
         INNER JOIN sys.schemas s
                    ON s.schema_id = t.schema_id
WHERE s.name = 'partial_maturity'
  AND t.name = 'app_user'
  AND c.name = 'created_at';

IF @ConstraintName IS NOT NULL
BEGIN
    SET @Sql =
        N'ALTER TABLE partial_maturity.app_user ' +
        N'DROP CONSTRAINT [' + @ConstraintName + N']';

EXEC sp_executesql @Sql;
END;


/*
 * updated_at
 */
SET @ConstraintName = NULL;

SELECT @ConstraintName = dc.name
FROM sys.default_constraints dc
         INNER JOIN sys.columns c
                    ON c.default_object_id = dc.object_id
         INNER JOIN sys.tables t
                    ON t.object_id = c.object_id
         INNER JOIN sys.schemas s
                    ON s.schema_id = t.schema_id
WHERE s.name = 'partial_maturity'
  AND t.name = 'app_user'
  AND c.name = 'updated_at';

IF @ConstraintName IS NOT NULL
BEGIN
    SET @Sql =
        N'ALTER TABLE partial_maturity.app_user ' +
        N'DROP CONSTRAINT [' + @ConstraintName + N']';

EXEC sp_executesql @Sql;
END;


/*
 * Modification des types SQL Server
 */
ALTER TABLE partial_maturity.app_user
ALTER COLUMN created_at DATETIMEOFFSET(7) NOT NULL;

ALTER TABLE partial_maturity.app_user
ALTER COLUMN updated_at DATETIMEOFFSET(7) NOT NULL;

ALTER TABLE partial_maturity.app_user
ALTER COLUMN last_login_at DATETIMEOFFSET(7) NULL;


/*
 * Recréation des valeurs par défaut
 */
ALTER TABLE partial_maturity.app_user
    ADD CONSTRAINT df_app_user_created_at
        DEFAULT SYSDATETIMEOFFSET() FOR created_at;

ALTER TABLE partial_maturity.app_user
    ADD CONSTRAINT df_app_user_updated_at
        DEFAULT SYSDATETIMEOFFSET() FOR updated_at;