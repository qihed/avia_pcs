package ru.mirea.avia.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.avia.dto.ChangePasswordRequest;
import ru.mirea.avia.dto.LoginRequest;
import ru.mirea.avia.dto.RefreshRequest;
import ru.mirea.avia.dto.TokenResponse;
import ru.mirea.avia.dto.UserResponse;
import ru.mirea.avia.security.CurrentUser;
import ru.mirea.avia.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Авторизация")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по логину и паролю")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return auth.login(request.login(), request.password(), userAgent);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновление access JWT по refresh-токену (токен ротируется)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return auth.refresh(request.refreshToken(), userAgent);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Выход: отзыв refresh-сессии")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Текущий пользователь")
    public UserResponse me() {
        return auth.currentUser(CurrentUser.require().id());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Смена пароля (в том числе обязательная смена временного); возвращает новые токены")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return auth.changePassword(CurrentUser.require().id(), request.currentPassword(), request.newPassword(), userAgent);
    }
}
