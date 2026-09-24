# Rôles et autorisations

## ADMIN

Actions autorisées :

- gérer les utilisateurs ;
- importer les fichiers CSV et consulter leur historique ;
- consulter les polices et simuler les intérêts ;
- consulter les paiements ;
- consulter le journal d’audit ;
- consulter le tableau de bord administratif.

Actions interdites :

- enregistrer un paiement ;
- annuler un paiement.

## COMPTABILITE

Actions autorisées :

- consulter les polices ;
- simuler les intérêts ;
- enregistrer un paiement total ;
- consulter l’historique et le détail des paiements ;
- annuler un paiement `PAID` avec un motif ;
- consulter le tableau de bord comptable.

Actions interdites :

- gérer les utilisateurs ;
- importer un fichier CSV ;
- consulter le journal d’audit.

## Réponses de sécurité

- utilisateur non authentifié : `HTTP 401 Unauthorized` ;
- utilisateur authentifié sans droit suffisant : `HTTP 403 Forbidden`.

## Principes d’implémentation

- les autorisations sont appliquées côté backend avec Spring Security ;
- les guards et masquages Angular améliorent l’expérience mais ne remplacent pas la sécurité backend ;
- seul `COMPTABILITE` peut modifier l’état financier par un paiement ou une annulation ;
- `ADMIN` peut consulter et simuler, sans valider d’opération financière.
