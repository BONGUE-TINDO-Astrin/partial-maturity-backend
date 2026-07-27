package com.belife.partial_maturity_backend.dtos.responses;

import com.belife.partial_maturity_backend.enums.UserRole;

public record CurrentUserResponse(
    Long id,
    String username,
    String fullName,
    UserRole role
) {
}
