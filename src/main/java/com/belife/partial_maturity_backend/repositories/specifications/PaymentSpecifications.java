package com.belife.partial_maturity_backend.repositories.specifications;

import com.belife.partial_maturity_backend.entities.PaymentEntity;
import com.belife.partial_maturity_backend.enums.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Construit les filtres optionnels de consultation
 * des paiements.
 *
 * <p>Chaque spécification retourne un critère neutre
 * lorsque son filtre n'est pas renseigné.</p>
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
        /*
         * Classe utilitaire non instanciable.
         */
    }

    /**
     * Filtre les paiements par statut.
     */
    public static Specification<PaymentEntity>
    hasStatus(PaymentStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Recherche une valeur dans le numéro de police.
     *
     * <p>La comparaison est insensible à la casse et
     * conserve les éventuels zéros initiaux.</p>
     */
    public static Specification<PaymentEntity>
    policyNumberContains(String search) {
        return (root, query, criteriaBuilder) -> {
            String normalizedSearch = normalizeSearch(search);

            if (normalizedSearch == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.like(
                criteriaBuilder.upper(root.get("policyNumber")),
                "%" + escapeLikePattern(normalizedSearch)
                        + "%",
                '\\'
            );
        };
    }

    /**
     * Supprime les espaces extérieurs et normalise
     * la recherche pour une comparaison insensible
     * à la casse.
     */
    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return search.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Échappe les caractères spéciaux de LIKE afin
     * qu'un numéro contenant %, _ ou \ soit recherché
     * comme une valeur littérale.
     */
    private static String escapeLikePattern(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}