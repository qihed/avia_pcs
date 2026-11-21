package ru.mirea.avia.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.avia.config.AppProperties;
import ru.mirea.avia.domain.Role;
import ru.mirea.avia.dto.AdminUserDtos.AdminUserResponse;
import ru.mirea.avia.dto.AdminUserDtos.CreateUserRequest;
import ru.mirea.avia.dto.AdminUserDtos.UpdateUserRequest;
import ru.mirea.avia.dto.AdminUserDtos.UserWithPasswordResponse;
import ru.mirea.avia.dto.PageResponse;
import ru.mirea.avia.filter.PageRequests;
import ru.mirea.avia.filter.UserFilter;
import ru.mirea.avia.service.UserAdminService;

import java.util.Map;
import java.util.UUID;

/** Учётные записи. Доступ только ADMIN — правило /api/admin/** в SecurityConfig. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    /** Поля сортировки: имя в запросе → атрибут UserAccount. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "login", "login",
            "displayName", "displayName",
            "createdAt", "createdAt",
            "lastLoginAt", "lastLoginAt");

    private final UserAdminService users;
    private final AppProperties props;

    public AdminUserController(UserAdminService users, AppProperties props) {
        this.users = users;
        this.props = props;
    }

    /** GET /api/admin/users?q=иван&role=MODERATOR&enabled=true&page=0&size=20&sort=login,asc */
    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) Role role,
                                                @RequestParam(required = false) Boolean enabled,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String sort) {
        int pageSize = size == null ? props.pagination().defaultPageSize() : size;
        return users.list(new UserFilter(q, role, enabled),
                PageRequests.of(page, pageSize, sort, props.pagination().maxPageSize(), SORT_FIELDS, "login,asc", "id"));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserWithPasswordResponse create(@Valid @RequestBody CreateUserRequest request) {
        return users.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/{id}/reset-password")
    public UserWithPasswordResponse resetPassword(@PathVariable UUID id) {
        return users.resetPassword(id);
    }

    @PostMapping("/{id}/unlock")
    public AdminUserResponse unlock(@PathVariable UUID id) {
        return users.unlock(id);
    }
}
