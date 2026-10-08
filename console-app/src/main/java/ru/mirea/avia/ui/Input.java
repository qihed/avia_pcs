package ru.mirea.avia.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import ru.mirea.avia.util.Validators;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.Function;

/** Чтение и первичная проверка ввода оператора с консоли. */
public class Input {
    /** Дата: день и месяц можно вводить одной или двумя цифрами, разделитель — точка, «/» или «-». */
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final Scanner scanner = new Scanner(System.in);

    public String text(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    public String required(String prompt) {
        while (true) {
            String value = text(prompt);
            if (!value.isBlank()) return value;
            System.out.println("Ошибка: значение не должно быть пустым.");
        }
    }
    public String maxLength(String prompt, int maxLength, boolean required) {
        while (true) {
            String value = text(prompt);

            if (required && value.isBlank()) {
                System.out.println("Ошибка: значение не должно быть пустым.");
                continue;
            }

            if (value.length() > maxLength) {
                System.out.println("Ошибка: нельзя вводить больше " + maxLength + " символов.");
                continue;
            }

            return value;
        }
    }
    public int integer(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(required(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите целое число.");
            }
        }
    }

    public long longNumber(String prompt) {
        while (true) {
            try {
                return Long.parseLong(required(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: ID должен быть целым числом.");
            }
        }
    }

    public BigDecimal decimal(String prompt) {
        while (true) {
            try {
                return new BigDecimal(required(prompt).replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите число.");
            }
        }
    }

    public LocalDate date(String prompt) {
        while (true) {
            String value = required(prompt + " (дд.ММ.гггг)").replace('/', '.').replace('-', '.');
            try {
                return LocalDate.parse(value, DATE);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: неверная дата. Пример: 05.03.1990.");
            }
        }
    }

    /** Дата рождения: корректная дата не позже сегодняшней; при ошибке вопрос повторяется. */
    public LocalDate birthDate(String prompt) {
        while (true) {
            LocalDate value = date(prompt);
            if (!value.isAfter(LocalDate.now())) return value;
            System.out.println("Ошибка: дата рождения не может быть в будущем.");
        }
    }

    /**
     * Часть ФИО: не длиннее maxLength и с заглавной буквы. При ошибке повторяется
     * только этот вопрос, уже введённые поля не сбрасываются.
     */
    public String name(String prompt, int maxLength, boolean required) {
        while (true) {
            String value = maxLength(prompt, maxLength, required);
            if (value.isBlank() || Validators.isValidName(value)) return value;
            System.out.println("Ошибка: должно начинаться с заглавной буквы и содержать только буквы (например, Иванов).");
        }
    }

    /** E-mail с символом @ и окончанием .com или .ru; при ошибке повторяется только этот вопрос. */
    public String email(String prompt) {
        while (true) {
            String value = required(prompt);
            if (Validators.isValidEmail(value)) return value;
            System.out.println("Ошибка: нужен символ @ и окончание .com или .ru (например, ivan@mail.ru).");
        }
    }

    /** Телефон: только цифры (10–15 штук); при ошибке повторяется только этот вопрос. */
    public String phone(String prompt) {
        while (true) {
            String value = required(prompt + " (только цифры, например 89161234567)");
            if (Validators.isValidPhone(value)) return value;
            System.out.println("Ошибка: номер должен состоять только из цифр, от 10 до 15 штук.");
        }
    }

    public LocalDateTime dateTime(String prompt) {
        while (true) {
            try {
                return LocalDateTime.parse(required(prompt + " (дд.ММ.гггг ЧЧ:мм)"), DATE_TIME);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: неверный формат даты и времени.");
            }
        }
    }

    /** Печатает пронумерованный список вариантов и возвращает выбранный. */
    public <E> E choice(String prompt, List<E> options, Function<E, String> title) {
        for (int i = 0; i < options.size(); i++) {
            System.out.println((i + 1) + ". " + title.apply(options.get(i)));
        }
        while (true) {
            int number = integer(prompt);
            if (number >= 1 && number <= options.size()) return options.get(number - 1);
            System.out.println("Ошибка: выберите пункт от 1 до " + options.size() + ".");
        }
    }

    /** Как {@link #choice}, но с пунктом «0. Назад»: при выборе 0 возвращает {@code null}. */
    public <E> E choiceOrBack(String prompt, List<E> options, Function<E, String> title) {
        for (int i = 0; i < options.size(); i++) {
            System.out.println((i + 1) + ". " + title.apply(options.get(i)));
        }
        System.out.println("0. Назад");
        while (true) {
            int number = integer(prompt);
            if (number == 0) return null;
            if (number >= 1 && number <= options.size()) return options.get(number - 1);
            System.out.println("Ошибка: выберите пункт от 0 до " + options.size() + ".");
        }
    }

    /** Подтверждение необратимой операции вводом «да» / «нет»; вопрос повторяется до ответа. */
    public boolean confirm(String prompt) {
        while (true) {
            String answer = text(prompt + " (да/нет)").toLowerCase(Locale.ROOT);
            if (answer.equals("да") || answer.equals("д")) return true;
            if (answer.equals("нет") || answer.equals("н")) return false;
            System.out.println("Ошибка: введите «да» или «нет».");
        }
    }
}
