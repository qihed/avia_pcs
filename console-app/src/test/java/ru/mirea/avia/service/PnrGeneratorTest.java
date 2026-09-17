package ru.mirea.avia.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет формат номера бронирования PNR (FR-20). */
class PnrGeneratorTest {
    private final PnrGenerator generator = new PnrGenerator(new Random(7));

    @Test
    void generatesSixLatinLettersAndDigits() {
        List<String> refs = Stream.generate(generator::generate).limit(500).toList();
        assertThat(refs)
                .allMatch(ref -> ref.matches("[A-Z0-9]{6}"))
                .allMatch(ref -> ref.matches(".*[A-Z].*"));
        assertThat(new PnrGenerator().generate()).matches("[A-Z0-9]{6}");
    }

    @Test
    void alwaysContainsLetterWhenRandomSourceYieldsDigitsFirst() {
        RandomGenerator digitsFirst = new RandomGenerator() {
            private int calls;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return calls++ < 6 ? 26 : 0;
            }
        };
        assertThat(new PnrGenerator(digitsFirst).generate()).isEqualTo("AAAAAA");
    }

    @Test
    void keepsDigitsWhenReferenceHasLetter() {
        RandomGenerator mixed = new RandomGenerator() {
            private final int[] indexes = {0, 1, 27, 28, 2, 3};
            private int calls;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return indexes[calls++ % indexes.length];
            }
        };
        assertThat(new PnrGenerator(mixed).generate()).isEqualTo("AB12CD");
    }
}
