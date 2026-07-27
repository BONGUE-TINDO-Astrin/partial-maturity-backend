package com.belife.partial_maturity_backend.mappers;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Configuration commune des mappers MapStruct.
 *
 * Toute propriété cible non alimentée déclenche une erreur
 * de compilation afin d'éviter les oublis silencieux.
 */
@MapperConfig(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface MapperConfiguration {
}