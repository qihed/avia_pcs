package ru.mirea.avia.dto;

public record TokenResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        boolean mustChangePassword,
        UserResponse user) {
}
