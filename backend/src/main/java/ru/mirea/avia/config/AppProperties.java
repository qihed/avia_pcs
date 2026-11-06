package ru.mirea.avia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Параметры приложения из application.yml (префикс app). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Bootstrap bootstrap,
        Cors cors,
        Security security,
        boolean permanentDeleteEnabled,
        Pagination pagination) {

    public record Jwt(String secretBase64, String issuer, long accessMinutes, long refreshDays) {
    }

    public record Bootstrap(String email, String name, String password) {
    }

    public record Cors(java.util.List<String> allowedOrigins) {
    }

    public record Security(int maxFailedAttempts, long lockMinutes, boolean swaggerAdminOnly) {
    }

    public record Pagination(int maxPageSize, int defaultPageSize) {
    }
}
