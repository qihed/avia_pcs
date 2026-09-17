package ru.mirea.avia.ui;

import ru.mirea.avia.dto.PassengerDtos.PassengerRequest;
import ru.mirea.avia.dto.PassengerDtos.PassengerResponse;
import ru.mirea.avia.service.PassengerService;

/**
 * Раздел «Пассажиры»: ведение карточек пассажиров (FR-03, UC-01).
 *
 * <p>Добавление карточки и список пассажиров доступны и разделу бронирований, чтобы
 * оформить бронь на нового пассажира, не выходя из диалога брони. В списках номер
 * документа маскируется (NFR-09), полностью он виден только в карточке.</p>
 */
public final class PassengersPane {
    private final PassengerService passengers;
    private final ConsoleIo io;
    private final InputPrompt prompt;
    private final CommandRunner runner;

    public PassengersPane(PassengerService passengers, ConsoleIo io, InputPrompt prompt, CommandRunner runner) {
        this.passengers = passengers;
        this.io = io;
        this.prompt = prompt;
        this.runner = runner;
    }

    /** Показывает меню раздела, пока оператор не выберет пункт 0. */
    public void show() {
        Menu.of("ПАССАЖИРЫ", io, prompt, runner)
                .item("Добавить пассажира", "passengers.create", this::createPassenger)
                .item("Список пассажиров", "passengers.list", this::printList)
                .item("Карточка пассажира по ID", "passengers.get", this::showCard)
                .item("Изменить данные пассажира", "passengers.update", this::edit)
                .item("Удалить пассажира", "passengers.delete", this::delete)
                .show();
    }

    /** Запрашивает данные нового пассажира, сохраняет карточку и возвращает её. */
    public PassengerResponse createPassenger() {
        PassengerRequest request = PassengerDialog.create(io, prompt, passengers);
        PassengerResponse saved = passengers.create(request);
        io.println();
        Ui.info(io, "Пассажир добавлен, ID = " + saved.id() + ": " + Cards.passengerSummary(saved));
        return saved;
    }

    /** Печатает список пассажиров с маскированными номерами документов. */
    public void printList() {
        io.println();
        Ui.info(io, "СПИСОК ПАССАЖИРОВ (номера документов маскированы)");
        Ui.table(io, Cards.passengerTable(), passengers.list());
    }

    private void showCard() {
        Ui.section(io, "КАРТОЧКА ПАССАЖИРА");
        PassengerResponse passenger = readPassenger();
        io.println();
        Cards.passenger(io, passenger, passengers.countBookings(passenger.id()));
    }

    private void edit() {
        Ui.section(io, "ИЗМЕНЕНИЕ ДАННЫХ ПАССАЖИРА");
        PassengerResponse existing = readPassenger();
        PassengerRequest request = PassengerDialog.edit(io, prompt, passengers, existing);
        PassengerResponse saved = passengers.update(existing.id(), request);
        io.println();
        Ui.info(io, "Данные пассажира сохранены: " + Cards.passengerSummary(saved));
    }

    private void delete() {
        Ui.section(io, "УДАЛЕНИЕ ПАССАЖИРА");
        PassengerResponse passenger = readPassenger();
        Ui.info(io, "Пассажир: " + Cards.passengerSummary(passenger));
        // Правило BR-10 проверяется до подтверждения: оператор не должен подтверждать невозможное удаление (TC-12).
        passengers.checkCanDelete(passenger.id());
        if (!prompt.confirm("Удалить карточку пассажира? Действие необратимо")) {
            Ui.info(io, "Удаление отменено, данные не изменены");
            return;
        }
        passengers.delete(passenger.id());
        Ui.info(io, "Пассажир " + passenger.shortName() + " удалён");
    }

    private PassengerResponse readPassenger() {
        return prompt.read("Введите ID пассажира", line -> passengers.get(InputPrompt.parseId(line)));
    }
}
