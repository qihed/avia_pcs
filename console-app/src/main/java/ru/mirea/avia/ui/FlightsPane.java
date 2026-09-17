package ru.mirea.avia.ui;

import ru.mirea.avia.domain.FlightStatus;
import ru.mirea.avia.dto.FlightDtos.FlightAvailability;
import ru.mirea.avia.dto.FlightDtos.FlightRequest;
import ru.mirea.avia.dto.FlightDtos.FlightResponse;
import ru.mirea.avia.service.FlightService;
import ru.mirea.avia.util.UiText;

/**
 * Раздел «Рейсы»: просмотр и ведение расписания (FR-04, UC-02, UC-09).
 *
 * <p>Список показывает свободные места каждого рейса. Отмена рейса и удаление требуют
 * подтверждения (NFR-17), а правило BR-10 проверяется ещё до вопроса оператору.</p>
 */
public final class FlightsPane {
    private final FlightService flights;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public FlightsPane(FlightService flights, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        this.flights = flights;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню раздела, пока оператор не выберет пункт 0. */
    public void show() {
        Menu.of("РЕЙСЫ", io, prompt, runner)
                .item("Добавить рейс", "flights.create", this::create)
                .item("Список рейсов (свободные места)", "flights.list", this::list)
                .item("Рейс по ID", "flights.get", this::showCard)
                .item("Изменить рейс", "flights.update", this::edit)
                .item("Сменить статус рейса", "flights.changeStatus", this::changeStatus)
                .item("Удалить рейс", "flights.delete", this::delete)
                .show();
    }

    private void create() {
        FlightRequest request = FlightDialog.create(io, prompt, flights);
        FlightResponse saved = flights.create(request);
        io.println();
        Ui.info(io, "Рейс добавлен, ID = " + saved.id() + ": " + Cards.flightLine(saved));
    }

    private void list() {
        io.println();
        Ui.info(io, "РАСПИСАНИЕ РЕЙСОВ");
        Ui.table(io, Cards.flightTable(), flights.list());
    }

    private void showCard() {
        Ui.section(io, "КАРТОЧКА РЕЙСА");
        FlightAvailability availability = readFlight();
        io.println();
        Cards.flight(io, availability);
    }

    private void edit() {
        Ui.section(io, "ИЗМЕНЕНИЕ РЕЙСА");
        FlightResponse existing = readFlight().flight();
        FlightRequest request = FlightDialog.edit(io, prompt, flights, existing);
        FlightResponse saved = flights.update(existing.id(), request);
        io.println();
        Ui.info(io, "Рейс сохранён: " + Cards.flightLine(saved));
        Ui.info(io, "Стоимость уже оформленных броней не пересчитывается");
    }

    private void changeStatus() {
        Ui.section(io, "СМЕНА СТАТУСА РЕЙСА");
        FlightAvailability availability = readFlight();
        FlightResponse flight = availability.flight();
        Ui.info(io, "Рейс " + Cards.flightLine(flight) + ", текущий статус: " + UiText.flightStatus(flight.status()));
        if (flight.allowedStatuses().isEmpty()) {
            Ui.info(io, "Рейс находится в финальном состоянии, смена статуса недоступна");
            return;
        }
        FlightStatus target = prompt.readChoice("Новый статус", flight.allowedStatuses(), UiText::flightStatus);
        // Правило E-306 проверяется до подтверждения: оператор не должен подтверждать невозможную отмену.
        flights.checkCanChangeStatus(flight.id(), target);
        if (target == FlightStatus.CANCELLED && !confirmCancel(availability)) {
            Ui.info(io, "Статус не изменён");
            return;
        }
        FlightResponse saved = flights.changeStatus(flight.id(), target);
        Ui.info(io, "Статус рейса " + saved.flightNumber() + " изменён: " + UiText.flightStatus(saved.status()));
    }

    private void delete() {
        Ui.section(io, "УДАЛЕНИЕ РЕЙСА");
        FlightResponse flight = readFlight().flight();
        Ui.info(io, "Рейс: " + Cards.flightLine(flight));
        // Правило BR-10 проверяется до подтверждения: оператор не должен подтверждать невозможное удаление.
        flights.checkCanDelete(flight.id());
        if (!prompt.confirm("Удалить рейс из расписания? Действие необратимо")) {
            Ui.info(io, "Удаление отменено, данные не изменены");
            return;
        }
        flights.delete(flight.id());
        Ui.info(io, "Рейс " + flight.flightNumber() + " удалён");
    }

    private boolean confirmCancel(FlightAvailability availability) {
        int active = availability.occupiedSeats();
        // Брони отменённого рейса не аннулируются автоматически, поэтому оператор узнаёт о них заранее.
        if (active > 0) {
            Ui.info(io, "Внимание: на рейс оформлено активных броней: " + active
                    + ". После отмены рейса их можно отменить в разделе «Бронирования» в любой момент.");
        }
        return prompt.confirm("Отменить рейс " + availability.flight().flightNumber() + "?");
    }

    private FlightAvailability readFlight() {
        return prompt.read("Введите ID рейса", line -> flights.get(InputPrompt.parseId(line)));
    }
}
