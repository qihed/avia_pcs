package ru.mirea.avia.ui;

import ru.mirea.avia.domain.BookingStatus;
import ru.mirea.avia.dto.BookingDtos.BookingResponse;
import ru.mirea.avia.error.ErrorCode;
import ru.mirea.avia.error.ValidationException;
import ru.mirea.avia.service.BookingService;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.service.PassengerService;
import ru.mirea.avia.util.MoneyFormat;
import ru.mirea.avia.util.UiText;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Раздел «Бронирования»: операции над основной сущностью системы (FR-05…FR-11).
 *
 * <p>Бронь выбирается по ID или PNR, после чего экран показывает её краткое описание,
 * заранее проверяет бизнес-правила и только затем спрашивает подтверждение. Поэтому
 * оператор не подтверждает действие, которое всё равно будет отклонено.</p>
 */
public final class BookingsPane {
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final String IRREVERSIBLE = "? Действие необратимо";

    private final BookingService bookings;
    private final PassengerService passengers;
    private final FlightService flights;
    private final PassengersPane passengersPane;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public BookingsPane(BookingService bookings, PassengerService passengers, FlightService flights,
                        PassengersPane passengersPane, ConsoleIo io, InputPrompt prompt,
                        CommandRunner runner) {
        this.bookings = bookings;
        this.passengers = passengers;
        this.flights = flights;
        this.passengersPane = passengersPane;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню раздела, пока оператор не выберет «Назад». */
    public void show() {
        Menu.of("БРОНИРОВАНИЯ", io, prompt, runner)
                .item("Создать бронирование", "bookings.create", this::create)
                .item("Список всех броней", "bookings.list", this::list)
                .item("Бронь по ID / PNR", "bookings.get", this::details)
                .item("Изменить бронь", "bookings.update", this::edit)
                .item("Сменить статус", "bookings.changeStatus", this::changeStatus)
                .item("Отменить бронь", "bookings.cancel", this::cancel)
                .item("Удалить бронь", "bookings.delete", this::delete)
                .show();
    }

    private void create() {
        BookingDialog.create(io, prompt, bookings, passengers, flights, passengersPane)
                .ifPresent(request -> printCreated(bookings.create(request)));
    }

    private void list() {
        io.println();
        Ui.info(io, "СПИСОК БРОНИРОВАНИЙ");
        Ui.table(io, Cards.bookingTable(), bookings.list());
    }

    private void details() {
        Ui.section(io, "КАРТОЧКА БРОНИРОВАНИЯ");
        BookingResponse booking = askBooking();
        io.println();
        Cards.booking(io, booking);
    }

    private void edit() {
        Ui.section(io, "ИЗМЕНЕНИЕ БРОНИРОВАНИЯ");
        BookingResponse booking = askBooking();
        Cards.note(io, Cards.bookingSummary(booking));
        bookings.checkCanModify(booking.id());
        BookingDialog.change(io, prompt, bookings, booking).ifPresent(request -> {
            BookingResponse saved = bookings.update(booking.id(), request);
            io.println();
            Cards.note(io, "Бронь изменена");
            Cards.booking(io, saved);
        });
    }

    private void changeStatus() {
        Ui.section(io, "СМЕНА СТАТУСА БРОНИРОВАНИЯ");
        BookingResponse booking = askBooking();
        Cards.note(io, Cards.bookingSummary(booking));
        io.println();
        printTransitions(booking);
        Ui.info(io, "Можно также ввести код статуса (PAID, CHECKED_IN, COMPLETED, CANCELLED…)");
        BookingStatus target = prompt.read("Выберите новый статус",
                line -> parseStatus(line, booking.allowedStatuses()));
        if (target == BookingStatus.CANCELLED && !confirmCancel(booking)) {
            Ui.info(io, "Статус не изменён");
            return;
        }

        BookingResponse saved = bookings.changeStatus(booking.id(), target);
        io.println();
        Cards.note(io, "Статус изменён: " + UiText.statusShort(booking.status())
                + " -> " + UiText.statusShort(saved.status()) + statusComment(saved));
    }

    private void cancel() {
        Ui.section(io, "ОТМЕНА БРОНИРОВАНИЯ");
        BookingResponse booking = askBooking();
        Cards.note(io, Cards.bookingSummary(booking));
        if (!confirmCancel(booking)) {
            Ui.info(io, "Бронь не отменена");
            return;
        }

        BookingResponse saved = bookings.cancel(booking.id());
        io.println();
        Cards.note(io, "Бронь " + saved.bookingRef() + " отменена. " + seatReleased(saved));
    }

    private void delete() {
        Ui.section(io, "УДАЛЕНИЕ БРОНИРОВАНИЯ");
        BookingResponse booking = askBooking();
        Cards.note(io, Cards.bookingSummary(booking));
        // BR-10 проверяется до подтверждения, чтобы оператор не подтверждал заведомо запрещённое удаление.
        bookings.checkCanDelete(booking.id());
        if (!prompt.confirm("Удалить бронь " + booking.bookingRef() + " из базы данных" + IRREVERSIBLE)) {
            Ui.info(io, "Удаление отменено");
            return;
        }

        bookings.delete(booking.id());
        io.println();
        Cards.note(io, "Бронь " + booking.bookingRef() + " удалена");
    }

    private BookingResponse askBooking() {
        return prompt.read("Введите ID или PNR брони", bookings::find);
    }

    private boolean confirmCancel(BookingResponse booking) {
        // BR-08 проверяется до подтверждения: поздняя отмена отклоняется без лишнего вопроса.
        bookings.checkCanCancel(booking.id());
        return prompt.confirm("Отменить бронь " + booking.bookingRef() + IRREVERSIBLE);
    }

    private void printTransitions(BookingResponse booking) {
        List<BookingStatus> allowed = booking.allowedStatuses();
        if (allowed.isEmpty()) {
            String reason = booking.status().isFinal() ? "финальный" : "меняется только системой";
            Ui.info(io, "Допустимых переходов нет: статус «" + UiText.status(booking.status()) + "» " + reason);
            return;
        }

        Ui.info(io, "Допустимые переходы:");
        for (int i = 0; i < allowed.size(); i++) {
            Cards.note(io, (i + 1) + ". " + UiText.status(allowed.get(i)));
        }
        Cards.note(io, "0. Назад");
    }

    private void printCreated(BookingResponse booking) {
        io.println();
        Cards.note(io, "Бронирование создано");
        Ui.field(io, "Номер брони (PNR)", booking.bookingRef());
        Ui.field(io, "Пассажир", booking.passenger().fullName());
        Ui.field(io, "Рейс", Cards.flightLine(booking.flight()));
        Ui.field(io, "Место / класс", Cards.seatLine(booking));
        Ui.field(io, "К оплате", MoneyFormat.formatRub(booking.price()));
        Ui.field(io, "Статус", UiText.status(booking.status()));
        Cards.note(io, "Внимание: бронь будет аннулирована автоматически через "
                + bookings.expireAfter().toMinutes() + " минут без оплаты");
    }

    private static BookingStatus parseStatus(String line, List<BookingStatus> allowed) {
        if (DIGITS.matcher(line).matches()) {
            if (allowed.isEmpty()) {
                throw new ValidationException(ErrorCode.E_105, "Для этой брони нет пунктов для выбора");
            }
            return allowed.get(InputPrompt.parseOption(line, allowed.size()) - 1);
        }
        // Код статуса вводится вручную, чтобы оператор мог проверить и запрещённый переход (TC-08).
        String code = line.toUpperCase(Locale.ROOT);
        return Arrays.stream(BookingStatus.values())
                .filter(status -> status.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new ValidationException(ErrorCode.E_103,
                        "Введите номер пункта или код статуса, например PAID"));
    }

    private static String statusComment(BookingResponse booking) {
        return switch (booking.status()) {
            case PAID -> ". Билет оформлен";
            case CHECKED_IN -> ". Пассажир зарегистрирован на рейс";
            case COMPLETED -> ". Перелёт выполнен, бронь закрыта";
            case CANCELLED -> ". " + seatReleased(booking);
            case CREATED, EXPIRED -> "";
        };
    }

    private static String seatReleased(BookingResponse booking) {
        return "Место " + booking.seatNumber() + " на рейсе " + booking.flight().flightNumber()
                + " возвращено в продажу";
    }
}
