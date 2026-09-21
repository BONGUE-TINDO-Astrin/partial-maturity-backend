/*
 * BeLife Insurance
 * Ajout du nom du client aux maturités de police.
 *
 * Principes :
 * - chaque maturité conserve le nom du client fourni
 *   dans le fichier source ;
 * - les données historiques reçoivent temporairement
 *   une valeur par défaut avant que la colonne devienne
 *   obligatoire ;
 * - la cohérence du nom pour une même police est
 *   contrôlée par le backend.
 */


/*
 * La colonne est d'abord nullable afin de permettre
 * la migration des données historiques existantes.
 */
ALTER TABLE partial_maturity.policy_maturity
    ADD client_name NVARCHAR(200) NULL;
GO


/*
 * Les anciennes maturités ne possèdent pas encore
 * le nom du client dans les données sources.
 *
 * Une valeur technique explicite permet de rendre
 * ensuite la colonne obligatoire sans inventer
 * un véritable nom de client.
 */
UPDATE partial_maturity.policy_maturity
SET client_name = N'CLIENT NON RENSEIGNE'
WHERE client_name IS NULL;
GO


/*
 * Les futurs imports doivent toujours fournir
 * un nom de client.
 */
ALTER TABLE partial_maturity.policy_maturity
ALTER COLUMN client_name NVARCHAR(200) NOT NULL;
GO


/*
 * Empêche les chaînes vides ou composées
 * uniquement d'espaces.
 */
ALTER TABLE partial_maturity.policy_maturity
    ADD CONSTRAINT ck_policy_maturity_client_name
        CHECK (LEN(LTRIM(RTRIM(client_name))) > 0 );
GO


/*
 * Facilite la recherche future par nom de client.
 *
 * Cet index n'impose pas l'unicité, car plusieurs
 * polices peuvent appartenir à des clients portant
 * le même nom.
 */
CREATE INDEX ix_policy_maturity_client_name
    ON partial_maturity.policy_maturity(client_name);
GO