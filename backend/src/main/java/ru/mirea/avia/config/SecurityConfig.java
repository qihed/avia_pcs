package ru.mirea.avia.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.mirea.avia.error.ErrorResponseWriter;
import ru.mirea.avia.security.JwtAuthenticationFilter;

import java.io.IOException;
import java.util.List;

/**
 * Права по ролям задаются здесь, в одном месте. Роли вложены: ADMIN включает EDITOR, EDITOR включает VIEWER.
 * Стандарт: чтение — VIEWER, изменение — EDITOR, справочники, пользователи, аудит, окончательное удаление — ADMIN.
 */
@Configuration
public class SecurityConfig {

    private static final String[] SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            AppProperties props, ErrorResponseWriter errors) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint(errors))
                        .accessDeniedHandler(deniedHandler(errors)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll();
                    auth.requestMatchers("/api/auth/**").authenticated();

                    if (props.security().swaggerAdminOnly()) {
                        auth.requestMatchers(SWAGGER).hasRole("ADMIN");
                    } else {
                        auth.requestMatchers(SWAGGER).permitAll();
                    }

                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/items/*/permanent").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.POST, "/api/references/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/api/references/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PATCH, "/api/references/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/references/**").hasRole("ADMIN");

                    auth.requestMatchers(HttpMethod.GET, "/api/**").hasRole("VIEWER");
                    auth.requestMatchers("/api/**").hasRole("EDITOR");

                    // статический web-интерфейс и прочее вне /api
                    auth.anyRequest().permitAll();
                });
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint entryPoint(ErrorResponseWriter errors) {
        return (HttpServletRequest req, HttpServletResponse res, org.springframework.security.core.AuthenticationException ex) ->
                write(errors, req, res, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется авторизация");
    }

    private AccessDeniedHandler deniedHandler(ErrorResponseWriter errors) {
        return (req, res, ex) -> write(errors, req, res, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав");
    }

    private void write(ErrorResponseWriter errors, HttpServletRequest req, HttpServletResponse res,
                       HttpStatus status, String code, String message) throws IOException {
        errors.write(req, res, status, code, message);
    }
}
