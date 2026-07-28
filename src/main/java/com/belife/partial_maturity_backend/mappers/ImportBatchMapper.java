package com.belife.partial_maturity_backend.mappers;

import com.belife.partial_maturity_backend.dtos.responses.ImportBatchSummaryResponse;
import com.belife.partial_maturity_backend.entities.ImportBatchEntity;
import org.mapstruct.Mapper;

/**
 * Convertit un lot d'import en réponse résumée.
 *
 * <p>Le détail des erreurs est géré dans le service,
 * car errorSummary est actuellement persisté sous forme textuelle.</p>
 */
@Mapper(config = MapperConfiguration.class)
public interface ImportBatchMapper {

    ImportBatchSummaryResponse toSummaryResponse(ImportBatchEntity entity);
}