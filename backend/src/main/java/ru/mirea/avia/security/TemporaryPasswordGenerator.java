package ru.mirea.avia.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Временный пароль для новой учётной записи и сброса пароля; всегда проходит {@link PasswordPolicy}. */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIAL = "!@#$%^&*-_=+";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[16];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SPECIAL);
        for (int i = 4; i < chars.length; i++) {
            chars[i] = pick(ALL);
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
