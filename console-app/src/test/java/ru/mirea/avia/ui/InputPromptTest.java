package ru.mirea.avia.ui;

import org.junit.jupiter.api.Test;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Проверяет разбор ID, ответов «да/нет» и номеров пунктов (коды E-101, E-103, E-105). */
class InputPromptTest {
    @Test
    void parsesPositiveId() {
        assertThat(InputPrompt.parseId("1")).isEqualTo(1L);
        assertThat(InputPrompt.parseId(" 42 ")).isEqualTo(42L);
        assertThat(InputPrompt.parseId("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsNonPositiveId() {
        assertThatThrownBy(() -> InputPrompt.parseId("0"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("ID должен быть целым числом")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_101);
        assertThatThrownBy(() -> InputPrompt.parseId("-5"))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_101);
    }

    @Test
    void rejectsNonNumericId() {
        assertThatThrownBy(() -> InputPrompt.parseId("abc"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("ID должен быть целым числом")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_101);
        assertThatThrownBy(() -> InputPrompt.parseId("")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseId("1.5")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseId("AB12CD")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseId("9223372036854775808")).isInstanceOf(ValidationException.class);
    }

    @Test
    void parsesYesAnswers() {
        assertThat(InputPrompt.parseYesNo("да")).isTrue();
        assertThat(InputPrompt.parseYesNo(" Да ")).isTrue();
        assertThat(InputPrompt.parseYesNo("Д")).isTrue();
        assertThat(InputPrompt.parseYesNo("YES")).isTrue();
        assertThat(InputPrompt.parseYesNo("y")).isTrue();
    }

    @Test
    void parsesNoAnswers() {
        assertThat(InputPrompt.parseYesNo("нет")).isFalse();
        assertThat(InputPrompt.parseYesNo("НЕТ")).isFalse();
        assertThat(InputPrompt.parseYesNo("н")).isFalse();
        assertThat(InputPrompt.parseYesNo("No")).isFalse();
        assertThat(InputPrompt.parseYesNo("n")).isFalse();
    }

    @Test
    void rejectsOtherAnswers() {
        assertThatThrownBy(() -> InputPrompt.parseYesNo("ага"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Введите «да» или «нет»")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_103);
        assertThatThrownBy(() -> InputPrompt.parseYesNo("")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseYesNo("1")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseYesNo("да нет")).isInstanceOf(ValidationException.class);
    }

    @Test
    void parsesOptionWithinRange() {
        assertThat(InputPrompt.parseOption("1", 3)).isEqualTo(1);
        assertThat(InputPrompt.parseOption(" 3 ", 3)).isEqualTo(3);
        assertThat(InputPrompt.parseOption("6", 6)).isEqualTo(6);
    }

    @Test
    void rejectsOptionOutsideRange() {
        assertThatThrownBy(() -> InputPrompt.parseOption("4", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Выберите пункт от 1 до 3")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_105);
        assertThatThrownBy(() -> InputPrompt.parseOption("0", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Выберите пункт от 1 до 3");
        assertThatThrownBy(() -> InputPrompt.parseOption("-1", 3)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNonNumericOption() {
        assertThatThrownBy(() -> InputPrompt.parseOption("бизнес", 3))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Выберите пункт от 1 до 3")
                .hasFieldOrPropertyWithValue("code", ErrorCode.E_105);
        assertThatThrownBy(() -> InputPrompt.parseOption("", 3)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> InputPrompt.parseOption("2.0", 3)).isInstanceOf(ValidationException.class);
    }
}
