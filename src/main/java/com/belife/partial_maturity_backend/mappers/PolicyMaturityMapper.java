package com.belife.partial_maturity_backend.mappers;

import com.belife.partial_maturity_backend.dtos.responses.PolicyMaturityResponse;
import com.belife.partial_maturity_backend.entities.PolicyMaturityEntity;
import org.mapstruct.Mapper;

/**
 * Convertit une maturité persistée en réponse API.
 */
@Mapper(config = MapperConfiguration.class)
public interface PolicyMaturityMapper {

    PolicyMaturityResponse toResponse(PolicyMaturityEntity entity);
}