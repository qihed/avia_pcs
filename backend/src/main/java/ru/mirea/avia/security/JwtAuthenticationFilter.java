package ru.mirea.avia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.domain.UserAccount;
import ru.mirea.avia.error.ErrorResponseWriter;
import ru.mirea.avia.repository.UserAccountRepository;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Читает Bearer-токен, загружает пользователя из БД (отключённый или удалённый не пройдёт)
 * и пока не сменён временный пароль пускает только на /api/auth/me, change-password и logout.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WITH_TEMP_PASSWORD = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout");

    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final ErrorResponseWriter errors;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, ErrorResponseWriter errors) {
        this.jwtService = jwtService;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            UserAccount user = jwtService.parseUserId(header.substring(7).trim())
                    .flatMap(users::findById)
                    .filter(UserAccount::isEnabled)
                    .orElse(null);
            if (user != null) {
                if (user.isMustChangePassword() && !ALLOWED_WITH_TEMP_PASSWORD.contains(request.getRequestURI())) {
                    errors.write(request, response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                            "Необходимо сменить временный пароль");
                    return;
                }
                authenticate(user);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(UserAccount user) {
        Set<Role> roles = Set.copyOf(user.getRoles());
        // Роль включает все нижестоящие: ADMIN получает ROLE_ADMIN, ROLE_EDITOR и ROLE_VIEWER
        List<GrantedAuthority> authorities = roles.stream()
                .flatMap(r -> r.withIncluded().stream())
                .map(Role::authority)
                .distinct()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        AuthUser principal = new AuthUser(user.getId(), user.getLogin(), user.getDisplayName(),
                user.isMustChangePassword(), roles);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
