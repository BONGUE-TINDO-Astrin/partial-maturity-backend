IF NOT EXISTS (
    SELECT 1
    FROM sys.schemas
    WHERE name = 'partial_maturity'
)
BEGIN
EXEC('CREATE SCHEMA partial_maturity');
END;