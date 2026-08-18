package com.belife.partial_maturity_backend.services.Impl;

import com.belife.partial_maturity_backend.dtos.responses.CsvImportResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import com.belife.partial_maturity_backend.enums.AuditEventType;
import com.belife.partial_maturity_backend.enums.AuditResourceType;
import com.belife.partial_maturity_backend.enums.ImportBatchStatus;
import com.belife.partial_maturity_backend.repositories.ImportBatchRepository;
import com.belife.partial_maturity_backend.repositories.PolicyMaturityRepository;
import com.belife.partial_maturity_backend.services.AuditService;
import com.belife.partial_maturity_backend.services.CsvImportPersistenceService;
import com.belife.partial_maturity_backend.services.models.AuditRecordCommand;
import com.belife.partial_maturity_backend.services.models.CsvValidationError;
import com.belife.partial_maturity_backend.services.models.ParsedMaturityRow;
import com.belife.partial_maturity_backend.utils.CsvValidationErrorJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persiste les résultats d'un chargement CSV.
 *
 * <p>Le lot valide et ses maturités sont enregistrés dans une seule
 * transaction. Une exception pendant une insertion entraîne donc
 * l'annulation complète du chargement.</p>
 */
@Service
@RequiredArgsConstructor
public class CsvImportPersistenceServiceImpl implements CsvImportPersistenceService {

    private final ImportBatchRepository importBatchRepository;
    private final PolicyMaturityRepository policyMaturityRepository;
    private final AuditService auditService;

    private final CsvValidationErrorJsonCodec errorJsonCodec;

    /**
     * Enregistre le lot valide et toutes les nouvelles maturités.
     *
     * <p>Cette méthode constitue la frontière atomique principale
     * du processus d'importation.</p>
     */
    @Override
    @Transactional
    public CsvImportResponse saveImportedBatch(
            String fileName,
            String fileSha256,
            long fileSize,
            int totalRows,
            int existingRows,
            List<ParsedMaturityRow> newRows,
            String currentUsername
    ) {
        Instant processingTime = Instant.now();

        ImportBatchEntity batch = new ImportBatchEntity();

        batch.setOriginalFileName(fileName);
        batch.setFileSha256(fileSha256);
        batch.setFileSizeBytes(fileSize);
        batch.setTotalRows(totalRows);
        batch.setInsertedRows(newRows.size());
        batch.setExistingRows(existingRows);
        batch.setErrorRows(0);
        batch.setStatus(ImportBatchStatus.IMPORTED);
        batch.setErrorSummary(null);
        batch.setImportedAt(processingTime);
        batch.setImportedBy(currentUsername);

        ImportBatchEntity savedBatch = importBatchRepository.save(batch);

        List<PolicyMaturityEntity> maturities =
            newRows.stream()
                .map(row -> toEntity(row, savedBatch))
                .toList();

        /*
         * saveAllAndFlush force l'exécution des contraintes SQL
         * avant la construction de la réponse.
         *
         * Si une contrainte échoue, toute la transaction est annulée,
         * y compris l'enregistrement du lot IMPORTED.
         */
        policyMaturityRepository.saveAllAndFlush(maturities);

        auditService.record(
            new AuditRecordCommand(
                AuditEventType.FILE_IMPORTED,
                AuditResourceType.IMPORT_BATCH,
                savedBatch.getId().toString(),
                null,
                currentUsername,
                "Importation réussie du fichier "
                        + savedBatch.getOriginalFileName()
                        + ".",
                Map.of(
                    "batchId",
                    savedBatch.getId(),
                    "fileName",
                    savedBatch.getOriginalFileName(),
                    "fileSha256",
                    savedBatch.getFileSha256(),
                    "fileSizeBytes",
                    savedBatch.getFileSizeBytes(),
                    "totalRows",
                    savedBatch.getTotalRows(),
                    "insertedRows",
                    savedBatch.getInsertedRows(),
                    "existingRows",
                    savedBatch.getExistingRows(),
                    "errorRows",
                    savedBatch.getErrorRows(),
                    "status",
                    savedBatch.getStatus().name()
                )
            )
        );

        return new CsvImportResponse(
                savedBatch.getId(),
                savedBatch.getOriginalFileName(),
                savedBatch.getStatus(),
                savedBatch.getTotalRows(),
                savedBatch.getInsertedRows(),
                savedBatch.getExistingRows(),
                savedBatch.getErrorRows(),
                processingTime,
                List.of()
        );
    }

    /**
     * Enregistre un chargement rejeté dans une transaction indépendante.
     *
     * <p>REQUIRES_NEW garantit que le rapport de rejet peut être
     * conservé même si un traitement appelant possède une transaction
     * qui doit être annulée.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CsvImportResponse saveRejectedBatch(
            String fileName,
            String fileSha256,
            long fileSize,
            int totalRows,
            List<CsvValidationError> errors,
            String currentUsername
    ) {
        Instant processingTime = Instant.now();

        ImportBatchEntity batch = new ImportBatchEntity();

        batch.setOriginalFileName(fileName);
        batch.setFileSha256(fileSha256);
        batch.setFileSizeBytes(fileSize);
        batch.setTotalRows(totalRows);
        batch.setInsertedRows(0);
        batch.setExistingRows(0);
        batch.setErrorRows(countAffectedRows(errors));
        batch.setStatus(ImportBatchStatus.REJECTED);
        batch.setErrorSummary(errorJsonCodec.write(errors));
        batch.setImportedAt(null);
        batch.setImportedBy(null);

        ImportBatchEntity savedBatch = importBatchRepository.saveAndFlush(batch);

        List<String> errorCodes =
                errors.stream()
                        .map(CsvValidationError::code)
                        .distinct()
                        .sorted()
                        .toList();

        auditService.record(
            new AuditRecordCommand(
                AuditEventType.FILE_REJECTED,
                AuditResourceType.IMPORT_BATCH,
                savedBatch.getId().toString(),
                null,
                currentUsername,
                "Rejet du fichier "
                        + savedBatch.getOriginalFileName()
                        + " avec "
                        + savedBatch.getErrorRows()
                        + " ligne(s) en erreur.",
                Map.of(
                    "batchId",
                    savedBatch.getId(),
                    "fileName",
                    savedBatch.getOriginalFileName(),
                    "fileSha256",
                    savedBatch.getFileSha256(),
                    "fileSizeBytes",
                    savedBatch.getFileSizeBytes(),
                    "totalRows",
                    savedBatch.getTotalRows(),
                    "insertedRows",
                    0,
                    "errorRows",
                    savedBatch.getErrorRows(),
                    "errorCount",
                    errors.size(),
                    "errorCodes",
                    errorCodes,
                    "status",
                    savedBatch.getStatus().name()
                )
            )
        );

        return new CsvImportResponse(
            savedBatch.getId(),
            savedBatch.getOriginalFileName(),
            savedBatch.getStatus(),
            savedBatch.getTotalRows(),
            savedBatch.getInsertedRows(),
            savedBatch.getExistingRows(),
            savedBatch.getErrorRows(),
            processingTime,
            errors
        );
    }

    private PolicyMaturityEntity toEntity(ParsedMaturityRow row, ImportBatchEntity batch) {
        PolicyMaturityEntity entity = new PolicyMaturityEntity();

        entity.setImportBatch(batch);
        entity.setPolicyNumber( row.policyNumber() );
        entity.setMaturityType( row.maturityType() );
        entity.setMaturityRank( row.maturityRank() );
        entity.setMaturityDate( row.maturityDate() );
        entity.setMaturityAmount( row.maturityAmount() );
        entity.setInterestEndDate( row.interestEndDate() );
        entity.setSourceRowNumber(row.rowNumber());

        return entity;
    }

    /**
     * Compte les lignes différentes concernées par les erreurs.
     *
     * L'erreur globale de ligne 0 est comptée comme une erreur,
     * même si aucune ligne métier précise n'est concernée.
     */
    private int countAffectedRows(List<CsvValidationError> errors) {
        long affectedRows = errors.stream()
            .map(CsvValidationError::rowNumber)
            .distinct()
            .count();

        return Math.toIntExact(affectedRows);
    }

}