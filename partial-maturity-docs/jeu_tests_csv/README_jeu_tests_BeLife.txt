JEU DE TEST CSV

Taux attendu dans la configuration backend : 0.035 (3,5 %).
Date de reference de conception du jeu : 2026-07-09.

ORDRE RECOMMANDE
1. Importer 01_maturites_initiales_valides.csv : 10 polices, 10 lignes inserees.
2. Importer 02_maturites_suivantes_valides.csv : rangs 2 a 4, chaque police reste a 4 maturites maximum.
3. Importer 03_doublons_identiques_valides.csv : aucune nouvelle ligne, lignes comptees comme existantes.
4. Importer ensuite les fichiers 04 a 10 separement : chacun doit etre rejete entierement (HTTP 422 avec rapport metier).

COUVERTURE DES 10 POLICES VALIDES
- BEL001 : 4 maturites, interets encore ouverts, plusieurs changements de date de reference.
- BEL002 : 2 maturites, date de fin des interets deja atteinte (2026-06-30).
- BEL003 : 3 maturites, une maturite exactement le 2026-07-09 et fin future.
- BEL004 : 4 maturites, dont des maturites futures pour verifier leur exclusion avant leur date.
- BEL005 : 1 maturite exactement a la date de fin des interets, aucun interet ulterieur.
- BEL006 : 2 maturites, longue anciennete et interets clotures depuis 2025-02-28.
- BEL007 : 3 maturites, cycles complets et periode incomplete.
- BEL008 : 2 maturites futures, police visible mais evenements futurs exclus de la situation courante.
- 000012345 : 4 maturites, verification de la conservation des zeros initiaux.
- BEL010 : 2 maturites, fin des interets future proche.

FICHIERS DE REJET
- 04 : tentative de modification de date_fin_interets d'une police existante.
- 05 : date_maturite posterieure a date_fin_interets.
- 06 : MATURITE_2 sans MATURITE_1.
- 07 : rang 2 date avant rang 1.
- 08 : meme police/rang mais montant contradictoire avec la base.
- 09 : dates de fin differentes pour une meme police dans un seul fichier.
- 10 : erreurs multiples (police absente, type, date, montant, date de fin).

TESTS PAIEMENTS APRES IMPORT
- Simuler BEL001 puis enregistrer un paiement : verifier PAYMENT_APPLIED et paidAmount exact.
- Annuler ce paiement : verifier son statut CANCELLED et son exclusion de la simulation suivante.
- Enregistrer un nouveau paiement de BEL001 apres annulation : la situation doit etre de nouveau payable.
- Simuler BEL002/BEL005/BEL006 : aucun interet ne doit etre ajoute apres date_fin_interets.
- Simuler BEL008 avant 2027-01-01 : les maturites futures ne doivent pas entrer dans le solde.
