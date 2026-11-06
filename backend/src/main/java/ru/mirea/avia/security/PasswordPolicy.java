package ru.mirea.avia.security;

import org.springframework.stereotype.Component;
import ru.mirea.avia.error.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Политика паролей: длина, набор символов, пароль не равен логину. */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    /** BCrypt учитывает только первые 72 байта пароля. */
    public static final int MAX_BYTES = 72;

    /** Возвращает список нарушений; пустой список — пароль допустим. */
    public List<String> violations(String password, String login) {
        List<String> result = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            result.add("пароль не задан");
            return result;
        }
        if (password.length() < MIN_LENGTH) {
            result.add("не короче " + MIN_LENGTH + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            result.add("не длиннее " + MAX_BYTES + " байт");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            result.add("минимум одна заглавная буква");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            result.add("минимум одна строчная буква");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            result.add("минимум одна цифра");
        }
        if (password.chars().allMatch(c -> Character.isLetterOrDigit(c))) {
            result.add("минимум один специальный символ");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            result.add("без пробелов");
        }
        if (login != null && login.length() >= 3) {
            String localPart = login.contains("@") ? login.substring(0, login.indexOf('@')) : login;
            if (localPart.length() >= 3 && password.toLowerCase().contains(localPart.toLowerCase())) {
                result.add("не должен содержать логин");
            }
        }
        return result;
    }

    public void validate(String password, String login, String field) {
        List<String> violations = violations(password, login);
        if (!violations.isEmpty()) {
            throw new BadRequestException("WEAK_PASSWORD", "Пароль не соответствует политике: " + String.join("; ", violations));
        }
    }
}
