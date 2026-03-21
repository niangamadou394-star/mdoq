package com.medoq.backend.dto.auth;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,       // seconds
    UserInfoDto user
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                   long expiresInMs, UserInfoDto user) {
        return new AuthResponse(
            accessToken,
            refreshToken,
            "Bearer",
            expiresInMs / 1000,
            user
        );
    }
}
