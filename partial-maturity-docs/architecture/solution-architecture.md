# Architecture de la solution

## 1. Vue générale

```text
Utilisateur
    |
    v
Frontend Angular
    |
    | HTTPS / JSON / JWT / Multipart
    v
Backend Spring Boot
    |
    | JPA / transactions / Flyway
    v
SQL Server
```

La solution est répartie dans trois dépôts indépendants : documentation, backend et frontend.

## 2. Frontend Angular

Responsabilités :

- affichage, navigation et formulaires ;
- gestion des états avec Signals ;
- appels HTTP avec RxJS ;
- ajout du JWT par interceptor ;
- guards et masquage des actions selon le rôle ;
- affichage des résultats financiers fournis par le backend.

Le frontend ne calcule pas les intérêts et ne décide pas du montant d’un paiement.

## 3. Backend Spring Boot

Responsabilités :

- authentification JWT et autorisations ;
- validation des requêtes et des CSV ;
- transactions métier ;
- calculs financiers avec `BigDecimal` ;
- paiements, annulations et concurrence ;
- journal d’audit ;
- agrégats des tableaux de bord ;
- accès à SQL Server.

Organisation principale :

```text
config
controllers
dtos
entities
enums
exceptions
mappers
repositories
services
serviceimpl
utils
```

## 4. SQL Server

Tables principales :

```text
APP_USER
IMPORT_BATCH
POLICY_MATURITY
PAYMENT
PAYMENT_DETAIL
AUDIT_LOG
FLYWAY_SCHEMA_HISTORY
```

Flyway gère toutes les évolutions du schéma. Une migration appliquée ne doit jamais être modifiée.

## 5. Sécurité

- mots de passe encodés avec BCrypt ;
- JWT signé côté backend ;
- secrets externalisés ;
- autorisations contrôlées par Spring Security ;
- paiement recalculé dans une transaction ;
- verrou pessimiste contre le double paiement ;
- audit sans données sensibles.

## 6. Transactions critiques

```text
Import réussi
→ lot + maturités + audit

Import rejeté
→ lot REJECTED + audit

Paiement
→ recalcul + PAYMENT + PAYMENT_DETAIL + audit

Annulation
→ statut CANCELLED + motif + audit
```

Chaque opération et son audit sont confirmés ou annulés ensemble.

## 7. Contrats d’échange

- JSON pour les API classiques ;
- multipart pour les fichiers CSV ;
- JWT dans le header `Authorization: Bearer <token>` ;
- erreurs backend structurées avec statut, code et message.

## 8. Principes de qualité

- DTO au lieu d’entités exposées ;
- `Clock` injectable pour la date métier ;
- calculs financiers centralisés ;
- six décimales pour les montants ;
- migrations versionnées ;
- tests automatisés et scénarios Postman ;
- documentation synchronisée avec le code.
