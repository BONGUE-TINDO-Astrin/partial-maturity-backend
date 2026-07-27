package com.belife.partial_maturity_backend.mappers;

import com.belife.partial_maturity_backend.dtos.responses.UserResponse;
import com.belife.partial_maturity_backend.entities.AppUserEntity;
import org.mapstruct.Mapper;

/**
 * Transforme une entité utilisateur en réponse API.
 *
 * Le mapper ne gère pas le mot de passe et ne contient
 * aucune règle métier.
 */
@Mapper(config = MapperConfiguration.class)
public interface AppUserMapper {

    UserResponse toResponse(AppUserEntity entity);
}