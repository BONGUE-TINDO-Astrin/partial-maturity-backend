# API et exploitation

## 1. Base URL

```text
http://localhost:8080/api/v1
```

Les endpoints protégés utilisent :

```http
Authorization: Bearer <token>
```

## 2. Principaux endpoints

### Authentification

```http
POST /api/v1/auth/login
GET  /api/v1/auth/me
```

### Utilisateurs, ADMIN

```http
GET   /api/v1/admin/users
POST  /api/v1/admin/users
PUT   /api/v1/admin/users/{userId}
PATCH /api/v1/admin/users/{userId}/status
```

### Imports CSV, ADMIN

```http
POST /api/v1/admin/imports/csv
GET  /api/v1/admin/imports
GET  /api/v1/admin/imports/{batchId}
GET  /api/v1/admin/imports/{batchId}/maturities
```

### Polices, ADMIN et COMPTABILITE

```http
GET /api/v1/policies/{policyNumber}
GET /api/v1/policies/{policyNumber}/simulation
```

### Paiements

```http
POST /api/v1/policies/{policyNumber}/payments
GET  /api/v1/policies/{policyNumber}/payments
GET  /api/v1/payments/{paymentId}
POST /api/v1/payments/{paymentId}/cancel
```

L’enregistrement et l’annulation sont réservés à `COMPTABILITE`. La consultation est autorisée à `ADMIN` et `COMPTABILITE`.

### Audit, ADMIN

```http
GET /api/v1/admin/audit
GET /api/v1/admin/audit/{auditId}
```

### Tableau de bord

```http
GET /api/v1/dashboard
```

Le backend adapte la réponse au rôle authentifié.

## 3. Contrat CSV

```csv
num_police;type_maturite;date_maturite;montant_maturite
POL001;MATURITE_1;2020-03-15;2500000.00
```

Contraintes :

- UTF-8, BOM accepté ;
- séparateur `;` ;
- date `yyyy-MM-dd` ;
- montant positif avec point décimal ;
- numéro de police traité comme chaîne ;
- taille maximale de 10 Mo ;
- import intégral ou rejet intégral.

## 4. Erreurs API

Format général :

```json
{
  "timestamp": "2026-07-31T08:00:00Z",
  "status": 422,
  "code": "PAYMENT_NOT_ALLOWED",
  "message": "La situation de la police est déjà soldée.",
  "details": []
}
```

Statuts courants :

```text
400 : requête invalide
401 : utilisateur non authentifié
403 : droit insuffisant
404 : ressource introuvable
409 : opération concurrente
422 : rejet métier
500 : erreur technique
```

## 5. Vérifications SQL Server

Historique Flyway :

```sql
SELECT installed_rank, version, description, script, success
FROM partial_maturity.flyway_schema_history
ORDER BY installed_rank;
```

Imports :

```sql
SELECT *
FROM partial_maturity.import_batch
ORDER BY id DESC;
```

Paiements :

```sql
SELECT *
FROM partial_maturity.payment
ORDER BY id DESC;
```

Audit :

```sql
SELECT *
FROM partial_maturity.audit_log
ORDER BY occurred_at DESC, id DESC;
```

## 6. Vérifications avant livraison

```bash
mvn clean compile
mvn test
mvn spring-boot:run
```

Contrôler ensuite :

- démarrage Flyway et Hibernate sans erreur ;
- authentification des deux rôles ;
- import valide et rejeté ;
- simulation avec cycles complets ;
- paiement et refus du double paiement ;
- annulation et réactivation du calcul ;
- audit des opérations sensibles ;
- tableaux de bord par rôle ;
- absence de secrets dans Git et les logs.

## 7. Exploitation

- sauvegarder régulièrement la base SQL Server ;
- ne jamais modifier une migration Flyway appliquée ;
- externaliser les secrets par environnement ;
- limiter les logs en production ;
- surveiller les échecs d’authentification et les conflits de paiement ;
- conserver le journal d’audit selon la politique de rétention de l’entreprise.
