package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.ImportBatchDetailResponse;
import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.dtos.responses.PageResponse;
import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.exceptions.ImportBatchNotFoundException;
import com.belife.partial_maturity_backend.mappers.ImportBatchMapper;
import com.belife.partial_maturity_backend.mappers.PolicyMaturityMapper;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.ImportBatchReversalEligibilityService;
import com.belife.partial_maturity_backend.services.ImportHistoryService;
import com.belife.partial_maturity_backend.services.models.ImportBatchReversalEligibility;
import com.belife.partial_maturity_backend.utils.CsvValidationErrorJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémente la consultation des chargements CSV.
 *
 * <p>Aucune méthode de ce service ne modifie les données.
 * Toutes les transactions sont donc déclarées en lecture seule.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportHistoryServiceImpl implements ImportHistoryService {

    private static final int MAXIMUM_PAGE_SIZE = 100;

    private final ImportBatchRepository importBatchRepository;
    private final PolicyMaturityRepository policyMaturityRepository;
    private final ImportBatchMapper importBatchMapper;
    private final PolicyMaturityMapper policyMaturityMapper;
    private final CsvValidationErrorJsonCodec errorJsonCodec;
    private final ImportBatchReversalEligibilityService reversalEligibilityService;

    @Override
    public PageResponse<ImportBatchSummaryResponse>
    getImportHistory(int page, int size) {
        int safePage = Math.max(page, 0);

        int safeSize = Math.min(Math.max(size, 1),MAXIMUM_PAGE_SIZE );

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ImportBatchEntity> batches =
                importBatchRepository
                        .findAllByOrderByCreatedAtDesc(pageable);

        List<ImportBatchSummaryResponse> content =
                batches.getContent()
                    .stream()
                    .map(importBatchMapper::toSummaryResponse)
                    .toList();

        return PageResponse.from(batches, content);
    }

    @Override
    public ImportBatchDetailResponse getImportDetail(
            Long batchId
    ) {
        ImportBatchEntity batch =
                findBatch(batchId);

        ImportBatchReversalEligibility eligibility =
                reversalEligibilityService.evaluate(
                        batch
                );

        return new ImportBatchDetailResponse(
                batch.getId(),
                batch.getOriginalFileName(),
                batch.getFileSha256(),
                batch.getFileSizeBytes(),
                batch.getTotalRows(),
                batch.getInsertedRows(),
                batch.getExistingRows(),
                batch.getErrorRows(),
                batch.getStatus(),
                batch.getImportedAt(),
                batch.getImportedBy(),
                batch.getReversedAt(),
                batch.getReversedBy(),
                batch.getReversalReason(),
                eligibility.reversible(),
                eligibility.blockedReason(),
                batch.getCreatedAt(),
                batch.getCreatedBy(),
                batch.getUpdatedAt(),
                batch.getUpdatedBy(),
                errorJsonCodec.read(
                        batch.getErrorSummary()
                )
        );
    }

    @Override
    public List<PolicyMaturityResponse>
    getImportedMaturities(Long batchId) {
        /*
         * Cette recherche garantit que l'API retourne 404
         * lorsque le lot n'existe pas, même si aucune maturité
         * ne lui est associée.
         */
        findBatch(batchId);

        return policyMaturityRepository
                .findAllByImportBatchIdOrderByPolicyNumberAscMaturityRankAsc(batchId)
                .stream()
                .map(policyMaturityMapper::toResponse)
                .toList();
    }

    private ImportBatchEntity findBatch(Long batchId) {
        return importBatchRepository
                .findById(batchId)
                .orElseThrow( () -> new ImportBatchNotFoundException(batchId));
    }
}
