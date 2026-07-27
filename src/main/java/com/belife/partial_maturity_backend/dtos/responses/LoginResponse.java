package com.belife.partial_maturity_backend.dtos.responses;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    CurrentUserResponse user
) {
}
