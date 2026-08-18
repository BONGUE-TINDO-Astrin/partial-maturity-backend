package com.belife.partial_maturity_backend.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralise la précision appliquée aux montants financiers.
 *
 * <p>Les simulations, paiements et détails de paiement utilisent
 * une échelle de six décimales et le mode HALF_UP.</p>
 *
 * <p>Cette classe ne doit pas être remplacée par des opérations
 * utilisant double ou float.</p>
 */
public final class FinancialAmountUtils {

    /**
     * Nombre de chiffres conservés après la virgule.
     */
    public static final int FINANCIAL_SCALE = 2;

    /**
     * Mode d'arrondi métier retenu pour le MVP.
     */
    public static final RoundingMode FINANCIAL_ROUNDING_MODE = RoundingMode.HALF_UP;

    private FinancialAmountUtils() {
        /*
         * Classe utilitaire non instanciable.
         */
    }

    /**
     * Normalise une valeur financière à six décimales.
     *
     * @param amount valeur à normaliser
     * @return valeur avec une échelle de six décimales
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }

        return amount.setScale(
                FINANCIAL_SCALE,
                FINANCIAL_ROUNDING_MODE
        );
    }

    /**
     * Retourne un zéro financier à six décimales.
     */
    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                FINANCIAL_SCALE,
                FINANCIAL_ROUNDING_MODE
        );
    }
}