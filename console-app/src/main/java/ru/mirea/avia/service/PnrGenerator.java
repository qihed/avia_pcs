package ru.mirea.avia.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Генерирует номер бронирования PNR (FR-20).
 *
 * <p>Номер состоит из шести символов набора {@code [A-Z0-9]} и всегда содержит хотя бы одну
 * букву, чтобы его нельзя было спутать с числовым ID брони. Уникальность номера проверяет
 * {@link BookingService}.</p>
 */
public final class PnrGenerator {
    private static final int LENGTH = 6;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final RandomGenerator random;

    public PnrGenerator() {
        this(new SecureRandom());
    }

    public PnrGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** Возвращает случайный номер брони из шести латинских букв и цифр. */
    public String generate() {
        String ref;
        do {
            ref = randomRef();
        } while (ref.chars().allMatch(Character::isDigit));
        return ref;
    }

    private String randomRef() {
        var builder = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
