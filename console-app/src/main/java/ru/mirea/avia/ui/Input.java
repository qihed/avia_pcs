package ru.mirea.avia.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

/** Чтение и первичная проверка ввода оператора с консоли. */
public class Input {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
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
            try {
                return LocalDate.parse(required(prompt + " (дд.ММ.гггг)"), DATE);
            } catch (DateTimeParseException e) {
                System.out.println("Ошибка: неверный формат даты.");
            }
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
}
