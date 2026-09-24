# BeLife Partial Maturity Documentation

Documentation fonctionnelle et architecturale du MVP de gestion des maturités partielles de BeLife Insurance.

## Dépôts de la solution

La solution est répartie dans trois dépôts indépendants :

- `belife-partial-maturity-docs` : règles métier, architecture et recette ;
- `belife-partial-maturity-backend` : API Spring Boot, SQL Server, calculs et sécurité ;
- `belife-partial-maturity-frontend` : interface Angular et intégration API.

## Fonctionnalités du MVP

- authentification et autorisations par rôle ;
- gestion des utilisateurs ;
- import transactionnel des maturités par CSV ;
- consultation des polices ;
- simulation des intérêts capitalisés ;
- paiement total et annulation ;
- journal d’audit ;
- tableaux de bord adaptés aux rôles.

## Documents

- [`business/business-rules.md`](business/business-rules.md) : règles métier de référence ;
- [`requirements/roles-and-permissions.md`](requirements/roles-and-permissions.md) : droits ADMIN et COMPTABILITE ;
- [`architecture/solution-architecture.md`](architecture/solution-architecture.md) : architecture globale.

## Principes essentiels

- le backend est la seule source de vérité métier et financière ;
- Angular n’effectue aucun calcul financier de référence ;
- les montants utilisent `BigDecimal` avec six décimales ;
- les opérations sensibles sont auditées ;
- aucun secret, mot de passe ou token ne doit être versionné.

## Version

- Produit : BeLife Partial Maturity
- Version documentaire : 1.0
- Statut : MVP
- Date : 31 juillet 2026
