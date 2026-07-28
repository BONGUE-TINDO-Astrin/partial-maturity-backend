package com.belife.partial_maturity_backend.controllers;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.services.CsvImportService;
import com.belife.partial_maturity_backend.services.ImportHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Expose l'importation et la consultation des fichiers CSV.
 *
 * <p>Toutes les opérations sont réservées au rôle ADMIN.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/imports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final ImportHistoryService importHistoryService;

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResponse> importCsv(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        CsvImportResponse response = csvImportService.importFile(file, authentication.getName());

        if ( response.status() == ImportBatchStatus.REJECTED) {
            return ResponseEntity.unprocessableEntity().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ImportBatchSummaryResponse>> getImportHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(importHistoryService.getImportHistory(page, size));
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<ImportBatchDetailResponse>getImportDetail(@PathVariable Long batchId) {
        return ResponseEntity.ok(importHistoryService.getImportDetail(batchId));
    }

    @GetMapping("/{batchId}/maturities")
    public ResponseEntity<List<PolicyMaturityResponse>>
    getImportedMaturities(@PathVariable Long batchId) {
        return ResponseEntity.ok(importHistoryService.getImportedMaturities(batchId));
    }
}