package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.requests.ReverseImportBatchRequest;
import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.services.CsvImportService;
import com.belife.partial_maturity_backend.services.ImportBatchReversalService;
import com.belife.partial_maturity_backend.services.ImportHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Expose l'importation, la consultation et la réversion
 * des chargements CSV.
 *
 * <p>La consultation est accessible à ADMIN et COMPTABILITE.
 * L'importation et la réversion sont réservées à ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'COMPTABILITE')")
public class CsvImportController {

    private final CsvImportService csvImportService;

    private final ImportHistoryService importHistoryService;

    private final ImportBatchReversalService importBatchReversalService;

    /**
     * Importe un fichier CSV de maturités.
     *
     * <p>Cette opération modifie les données des polices
     * et reste donc réservée à ADMIN.</p>
     */
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResponse> importCsv(
            @RequestPart("file")
            MultipartFile file,

            Authentication authentication
    ) {
        CsvImportResponse response = csvImportService.importFile(file, authentication.getName());

        if (response.status()== ImportBatchStatus.REJECTED) {
            return ResponseEntity.unprocessableEntity().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retourne l'historique paginé des chargements.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ImportBatchSummaryResponse>> getImportHistory(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok(importHistoryService.getImportHistory(page, size));
    }

    /**
     * Retourne le détail d'un chargement.
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<ImportBatchDetailResponse>
    getImportDetail(@PathVariable Long batchId) {
        return ResponseEntity.ok(importHistoryService.getImportDetail(batchId));
    }

    /**
     * Retourne les maturités encore actives
     * introduites par un chargement.
     *
     * <p>Après une réversion réussie, cette liste
     * est vide.</p>
     */
    @GetMapping("/{batchId}/maturities")
    public ResponseEntity<List<PolicyMaturityResponse>> getImportedMaturities(
            @PathVariable
            Long batchId
    ) {
        return ResponseEntity.ok(importHistoryService.getImportedMaturities(batchId));
    }

    /**
     * Annule un chargement précédemment importé.
     *
     * <p>Les maturités introduites par le lot sont retirées
     * uniquement lorsque :</p>
     *
     * <ul>
     *     <li>aucun paiement PAID ne dépend des polices ;</li>
     *     <li>les rangs restants demeurent continus ;</li>
     *     <li>les dates restantes demeurent cohérentes.</li>
     * </ul>
     *
     */
    @PostMapping("/{batchId}/reverse")
    public ResponseEntity<ImportBatchDetailResponse>
    reverseImportBatch(
            @PathVariable
            Long batchId,

            @Valid
            @RequestBody
            ReverseImportBatchRequest request,

            Authentication authentication
    ) {
        return ResponseEntity.ok(
                importBatchReversalService.reverseImportBatch(batchId, request, authentication.getName())
        );
    }
}