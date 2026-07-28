package com.belife.partial_maturity_backend.dtos.responses;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Format stable des réponses paginées de l'API.
 *
 * <p>Le frontend ne dépend ainsi pas directement du format interne
 * de sérialisation des objets Page de Spring Data.</p>
 *
 * @param content éléments de la page courante
 * @param page numéro de page, commençant à zéro
 * @param size taille demandée
 * @param totalElements nombre total d'éléments
 * @param totalPages nombre total de pages
 * @param first indique si la page est la première
 * @param last indique si la page est la dernière
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /**
     * Construit une réponse stable à partir d'une page Spring Data.
     */
    public static <T> PageResponse<T> from(Page<?> sourcePage, List<T> content) {
        return new PageResponse<>(
                List.copyOf(content),
                sourcePage.getNumber(),
                sourcePage.getSize(),
                sourcePage.getTotalElements(),
                sourcePage.getTotalPages(),
                sourcePage.isFirst(),
                sourcePage.isLast()
        );
    }
}