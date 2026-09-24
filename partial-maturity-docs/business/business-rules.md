# Règles métier

## 1. Utilisateurs

- Deux rôles existent : `ADMIN` et `COMPTABILITE`.
- Un compte inactif ne peut pas s’authentifier.
- Les identifiants sont uniques sans tenir compte de la casse.
- Le système conserve toujours au moins un administrateur actif.
- Aucun mot de passe ou hash ne doit être exposé ou audité.

## 2. Import CSV

Format obligatoire :

```csv
num_police;type_maturite;date_maturite;montant_maturite
```

Règles principales :

- encodage UTF-8, BOM accepté ;
- séparateur point-virgule ;
- date au format `yyyy-MM-dd` ;
- montant positif avec point décimal ;
- numéro de police traité comme une chaîne, avec conservation des zéros initiaux ;
- import intégral ou rejet intégral, sans insertion partielle.

Une maturité est identifiée par :

```text
numéro de police + rang de maturité
```

Une maturité identique est conservée sans duplication. Une différence de type, date ou montant pour la même clé métier provoque le rejet complet du fichier.

Les rangs doivent être continus à partir de 1 et les dates strictement croissantes.

## 3. Calcul des intérêts

- Taux annuel fixe : `3,2 %`, représenté par `0.032`.
- Date métier : `LocalDate.now(clock)` côté backend.
- Les intérêts sont appliqués uniquement par cycles complets de 12 mois.
- Aucun prorata journalier ou mensuel n’est utilisé.
- Les intérêts sont capitalisés.

Pour chaque cycle :

```text
intérêt = solde courant × 0.032
nouveau solde = solde courant + intérêt
```

Lors d’une nouvelle maturité :

```text
1. appliquer les cycles complets échus ;
2. ajouter les intérêts ;
3. ajouter le montant de la maturité ;
4. utiliser sa date comme nouvelle référence.
```

À date identique, une maturité est traitée avant un paiement.

## 4. Précision financière

- Tous les calculs utilisent `BigDecimal`.
- Les montants de simulation et de paiement utilisent six décimales.
- Le mode d’arrondi est `HALF_UP`.
- Angular affiche les valeurs du backend sans les recalculer.

## 5. Paiements

- Le MVP autorise uniquement le paiement total.
- Angular n’envoie aucun montant faisant autorité.
- Le backend verrouille la situation, recalcule le solde puis enregistre le paiement.
- Le statut initial est `PAID`.
- Un paiement `PAID` remet le capital, les intérêts et le solde ouverts à zéro.
- Un second paiement de la même situation est refusé.

## 6. Annulation

- Seul un paiement `PAID` peut être annulé.
- Le motif est obligatoire et limité à 500 caractères.
- Le statut devient `CANCELLED`.
- Aucune donnée n’est supprimée.
- Un paiement annulé est exclu des simulations, ce qui réactive la situation financière correspondante.

## 7. Audit

Événements audités :

```text
USER_CREATED
USER_UPDATED
USER_ACTIVATED
USER_DEACTIVATED
FILE_IMPORTED
FILE_REJECTED
PAYMENT_RECORDED
PAYMENT_CANCELLED
```

Une opération réussie produit exactement un événement. Une opération refusée n’en produit aucun.

Le journal ne doit contenir aucun mot de passe, hash, JWT, token, secret ou contenu CSV confidentiel.
