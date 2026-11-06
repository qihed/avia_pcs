package ru.mirea.avia;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.mirea.avia.error.BadRequestException;
import ru.mirea.avia.security.PasswordPolicy;
import ru.mirea.avia.security.TemporaryPasswordGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Политика паролей и BCrypt. */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.violations("Str0ng!Passw0rd", "user@example.org")).isEmpty();
    }

    @Test
    void rejectsShortPassword() {
        assertThat(policy.violations("Ab1!xyz", "user@example.org")).anyMatch(v -> v.contains("не короче"));
    }

    @Test
    void rejectsMissingCharacterClasses() {
        assertThat(policy.violations("alllowercase1!", "u@x.org")).anyMatch(v -> v.contains("заглавная"));
        assertThat(policy.violations("ALLUPPERCASE1!", "u@x.org")).anyMatch(v -> v.contains("строчная"));
        assertThat(policy.violations("NoDigitsHere!!", "u@x.org")).anyMatch(v -> v.contains("цифра"));
        assertThat(policy.violations("NoSpecial12345", "u@x.org")).anyMatch(v -> v.contains("специальный"));
    }

    @Test
    void rejectsPasswordContainingLogin() {
        assertThat(policy.violations("Maria123!Pass", "maria@example.org")).anyMatch(v -> v.contains("логин"));
    }

    @Test
    void rejectsTooLongForBcrypt() {
        assertThat(policy.violations("Aa1!" + "x".repeat(80), "u@x.org")).anyMatch(v -> v.contains("72"));
    }

    @Test
    void validateThrowsBadRequest() {
        assertThatThrownBy(() -> policy.validate("weak", "u@x.org", "newPassword"))
                .isInstanceOf(BadRequestException.class);
    }

    @RepeatedTest(20)
    void generatedTemporaryPasswordPassesPolicy() {
        String password = new TemporaryPasswordGenerator().generate();
        assertThat(policy.violations(password, "someone@example.org")).isEmpty();
    }

    @Test
    void bcryptHashDoesNotContainPlainPassword() {
        var encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("Str0ng!Passw0rd");
        assertThat(hash).doesNotContain("Str0ng").startsWith("$2");
        assertThat(encoder.matches("Str0ng!Passw0rd", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
