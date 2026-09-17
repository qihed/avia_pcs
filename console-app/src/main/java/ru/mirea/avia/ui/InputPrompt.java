package ru.mirea.avia.ui;

import ru.mirea.avia.error.AppException;
import ru.mirea.avia.error.DataAccessException;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.SeatUnavailableException;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.util.DateTimeFormat;
import ru.mirea.avia.util.MoneyFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Безопасное чтение и первичная проверка ввода оператора (FR-02, коды E-101…E-105).
 *
 * <p>При ошибке формата или бизнес-правила значение запрашивается повторно, а оператор
 * видит пояснение. Ввод {@code 0} в любом поле прерывает операцию через
 * {@link InputCancelledException}. Ошибки базы данных не перехватываются: их выводит
 * {@link CommandRunner}.</p>
 */
public final class InputPrompt {
    public static final String CANCEL = "0";

    private final ConsoleIo io;

    public InputPrompt(ConsoleIo io) {
        this.io = io;
    }

    /** Печатает подсказку и возвращает введённую строку без пробелов по краям. */
    public String readLine(String label) {
        io.print("  " + label + ": ");
        return io.readLine().trim();
    }

    /** Повторяет запрос, пока {@code parser} не вернёт значение; ввод {@code 0} отменяет операцию. */
    public <T> T read(String label, Function<String, T> parser) {
        while (true) {
            String line = readLine(label);
            if (CANCEL.equals(line)) throw new InputCancelledException();
            try {
                return parser.apply(line);
            } catch (DataAccessException ex) {
                throw ex;
            } catch (AppException ex) {
                printRetry(ex);
            }
        }
    }

    /** Запрашивает значение в диалоге изменения: пустой ввод оставляет текущее значение. */
    public <T> T readOrKeep(String label, T current, String currentText, Function<String, T> parser) {
        return read(label + " [" + currentText + "]", line -> line.isEmpty() ? current : parser.apply(line));
    }

    /** Запрашивает ID записи — целое положительное число (E-101). */
    public long readId(String label) {
        return read(label, InputPrompt::parseId);
    }

    /** Запрашивает непустую строку (E-104). */
    public String readRequired(String label, String fieldTitle) {
        return read(label, line -> required(line, fieldTitle));
    }

    /** Запрашивает дату в формате дд.ММ.гггг (E-102). */
    public LocalDate readDate(String label) {
        return read(label + " (" + DateTimeFormat.DATE_PATTERN + ")", DateTimeFormat::parseDate);
    }

    /** Запрашивает дату и время в формате дд.ММ.гггг ЧЧ:мм (E-102). */
    public LocalDateTime readDateTime(String label) {
        return read(label + " (" + DateTimeFormat.DATE_TIME_PATTERN + ")", DateTimeFormat::parseDateTime);
    }

    /** Запрашивает сумму; если {@code optional}, пустой ввод возвращает {@code null} (E-103). */
    public BigDecimal readMoney(String label, boolean optional) {
        return read(label, line -> optional && line.isEmpty() ? null : MoneyFormat.parse(line));
    }

    /** Запрашивает подтверждение «да/нет» (NFR-17). */
    public boolean confirm(String question) {
        return read(question + " (да/нет)", InputPrompt::parseYesNo);
    }

    /** Запрашивает выбор варианта по номеру: «1 - Эконом, 2 - Комфорт, 3 - Бизнес» (E-105). */
    public <E> E readChoice(String label, List<E> options, Function<E, String> title) {
        int index = read(label + " (" + hint(options, title) + ")", line -> parseOption(line, options.size()));
        return options.get(index - 1);
    }

    /** Запрашивает выбор варианта в диалоге изменения: пустой ввод оставляет текущий вариант. */
    public <E> E readChoiceOrKeep(String label, List<E> options, Function<E, String> title, E current) {
        String text = label + " [" + title.apply(current) + "] (" + hint(options, title) + ", Enter — оставить)";
        return read(text, line -> line.isEmpty() ? current : options.get(parseOption(line, options.size()) - 1));
    }

    /** Запрашивает пункт меню от 0 до {@code max}; при ошибке сообщает о ней и возвращает -1 (E-105). */
    public int readMenuChoice(int max) {
        String line = readLine("Выберите действие");
        try {
            int value = Integer.parseInt(line);
            if (value >= 0 && value <= max) return value;
        } catch (NumberFormatException ex) {
            // Нечисловой ввод обрабатывается так же, как номер вне диапазона.
        }
        Ui.error(io, "выберите пункт от 0 до " + max + ".");
        return -1;
    }

    /** Ожидает Enter, чтобы оператор успел прочитать результат операции (п. 10.1 ТЗ). */
    public void pause() {
        io.println();
        io.print("  Нажмите Enter для возврата в меню...");
        io.readLine();
    }

    /** Разбирает ID записи и отклоняет нечисловое или неположительное значение с кодом E-101. */
    public static long parseId(String text) {
        try {
            long id = Long.parseLong(text.trim());
            if (id > 0) return id;
        } catch (NumberFormatException ex) {
            // Нечисловой ввод отклоняется тем же сообщением, что и неположительный ID.
        }
        throw new ValidationException(ErrorCode.E_101, "ID должен быть целым числом");
    }

    /** Разбирает целое число и отклоняет нечисловой ввод с кодом E-101. */
    public static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(ErrorCode.E_101, "Значение должно быть целым числом");
        }
    }

    /** Разбирает ответ «да/нет» и отклоняет прочие ответы с кодом E-103. */
    public static boolean parseYesNo(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "да", "д", "yes", "y" -> true;
            case "нет", "н", "no", "n" -> false;
            default -> throw new ValidationException(ErrorCode.E_103, "Введите «да» или «нет»");
        };
    }

    /** Разбирает номер пункта от 1 до {@code max} и отклоняет прочие значения с кодом E-105. */
    public static int parseOption(String text, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 1 && value <= max) return value;
        } catch (NumberFormatException ex) {
            // Нечисловой ввод отклоняется тем же сообщением, что и номер вне диапазона.
        }
        throw new ValidationException(ErrorCode.E_105, "Выберите пункт от 1 до " + max);
    }

    private void printRetry(AppException ex) {
        String message = ex.getMessage();
        // Сообщения исключений пишутся без точки, но составные сообщения могут её уже содержать.
        String sentence = message.endsWith(".") ? message : message + ".";
        // Подсказка о другом месте уместна только когда занято конкретное место, а не весь рейс.
        boolean seatTaken = ex instanceof SeatUnavailableException && message.startsWith("Место ");
        String hint = seatTaken ? "Выберите другое место." : "Повторите ввод.";
        // Ошибка формата ввода (E-1xx) помечается явно, как в диалоге п. 10.3 ТЗ.
        String text = ex instanceof ValidationException ? "Ошибка: " + lowerFirst(sentence) : sentence;
        io.println("  " + text + " " + hint);
    }

    /** «Фамилия …» → «фамилия …»; аббревиатуры вроде «ID» и «PNR» не меняются. */
    private static String lowerFirst(String text) {
        boolean word = text.length() > 1
                && Character.isUpperCase(text.charAt(0))
                && Character.isLowerCase(text.charAt(1));
        return word ? text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1) : text;
    }

    private static String required(String text, String fieldTitle) {
        if (text.isBlank()) {
            throw new ValidationException(ErrorCode.E_104, "Поле «" + fieldTitle + "» не может быть пустым");
        }
        return text;
    }

    private static <E> String hint(List<E> options, Function<E, String> title) {
        return IntStream.range(0, options.size())
                .mapToObj(i -> (i + 1) + " - " + title.apply(options.get(i)))
                .collect(Collectors.joining(", "));
    }
}
