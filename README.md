# Авиабронирование — консольная ИС (КР №1)

Console UI → Service → Repository/JDBC → PostgreSQL. Вариант: пассажирские авиаперевозки.

## Сущности

- `Passenger` — пассажир (участник предметной области);
- `Flight` — рейс (создание + список + просмотр по ID, без редактирования);
- `Booking` — бронирование (основная сущность, полный CRUD + бизнес-правила).

## Что реализовано

- CRUD для `Booking`: создание, список, получение по ID, изменение, удаление, смена статуса;
- `enum BookingStatus` (CREATED, PAID, CHECKED_IN, COMPLETED, CANCELLED) с матрицей допустимых переходов;
- 6 бизнес-правил в Java-коде (`BookingService`): существование пассажира и рейса, рейс открыт
  для продажи, место свободно, у пассажира нет второй активной брони на этот рейс, цена больше нуля,
  запрещённый переход статуса;
- обработка нечислового ввода, отсутствующего ID, нарушения бизнес-правил, ошибок JDBC —
  программа не падает, а печатает понятное сообщение и возвращается в меню;
- 2 способа поиска (по фамилии пассажира, по номеру рейса), 2 фильтра (по статусу, по классу
  обслуживания), 2 сортировки (по цене, по дате вылета);
- статистика из 6 показателей;
- экспорт бронирований в Excel `.xlsx`;
- отдельный пункт меню для вывода таблиц `passengers`, `flights`, `bookings`;
- JDBC: `Connection`, `PreparedStatement`, `ResultSet`, try-with-resources, параметризованные запросы;
- интерфейс `BookingRepository` и реализация `JdbcBookingRepository` — полиморфизм;
- Stream API для поиска, фильтрации, сортировки и статистики;
- собственные исключения `BusinessException`, `EntityNotFoundException`.

## Структура проекта

```
src/main/java/ru/mirea/avia/
├── Main.java
├── model/        Passenger, Flight, FlightStatus, Booking, BookingStatus, FareClass, DocumentType
├── repository/   PassengerRepository, FlightRepository, BookingRepository (интерфейс),
│                 JdbcBookingRepository (реализация)
├── service/      PassengerService, FlightService, BookingService
├── exception/    BusinessException, EntityNotFoundException
├── ui/           ConsoleUI, Input
└── util/         DatabaseManager, ExcelExporter
```

## Запуск

Из корня репозитория (нужен JDK 21; для `docker` — Docker Desktop):

```bash
./avia up docker       # БД в контейнере (порт 5441), схема и демо-данные применяются сами
./avia fx docker       # JavaFX-клиент (КР2)
./avia console docker  # консольное приложение (КР1)
```

Другие команды: `./avia down [env]` (остановить БД, данные сохраняются), `./avia reset [env]`
(пересоздать БД с демо-данными), `./avia psql [env]`. Окружение — `docker` (по умолчанию) или `local`
(PostgreSQL на компьютере, порт 5432); параметры лежат в `env/<имя>.env`, личные правки — в
`env/<имя>.local.env` (игнорируется git). Подключение к БД берётся из переменных `DB_URL`,
`DB_USER`, `DB_PASSWORD`, иначе из `console-app/src/main/resources/database.properties`.

В IntelliJ IDEA: открыть `console-app/pom.xml` как Maven-проект, JDK 21. Точки входа:
`ru.mirea.avia.Main` (консоль) и `ru.mirea.avia.fx.FxLauncher` (JavaFX).

### JavaFX-клиент: как добавить экран

Экран — это `fxml/<Имя>View.fxml` без `fx:controller` и контроллер в `fx/controller`, которому сервисы
передаются через конструктор (см. `MainApp`, `BookingsController`, `PassengerViewController`). Кнопку в меню
создаёт одна строка в `MainApp`: `main.addScreen("Рейсы", flightsLoader.load(), flightsController::refresh)` —
последний аргумент вызывается при каждом переходе на экран, чтобы перечитать данные. Общие стили — `css/app.css`. Добавление и изменение брони — `BookingFormDialog.fxml` +
`BookingFormController`; кнопка «Сменить статус» пока сообщает, что функция в разработке.

### Формы пассажира и рейса

`PassengerFormDialog.fxml` + `PassengerFormController` и `FlightFormDialog.fxml` + `FlightFormController` —
модальные формы «Добавить / Изменить». Ошибки ввода показываются красным под полем, ошибки бизнес-правил
(дубль e-mail, рейс с таким номером в этот день уже есть, мест меньше, чем броней) — плашкой сверху формы.
Окно не закрывается, пока данные не сохранены. Вызов из экранов «Пассажиры» и «Рейсы»:

```java
Window owner = table.getScene().getWindow();
PassengerFormController.open(owner, passengerService, null).ifPresent(p -> onRefresh());      // добавить
PassengerFormController.open(owner, passengerService, selected).ifPresent(p -> onRefresh());  // изменить
FlightFormController.open(owner, flightService, null).ifPresent(f -> onRefresh());
FlightFormController.open(owner, flightService, selected).ifPresent(f -> onRefresh());
```

Общая обвязка (`FormDialogs`, `FormErrors`, `FormFields`) подойдёт и для формы брони.

Формы пассажира открываются с экрана «Пассажиры» (кнопки и двойной клик по строке). Пока экрана «Рейсы» нет,
формы рейса открываются из раздела «Формы» в левом меню главного окна; «Изменить…» сначала предлагает выбрать
запись из списка. Когда экран появится, раздел убирается из `MainView.fxml`, а вызовы переезжают в его контроллер.

### Экран «Пассажиры»

`PassengerView.fxml` + `PassengerViewController`: таблица пассажиров с поиском (ФИО, документ, e-mail, телефон)
и фильтром по типу документа, сортировка кликом по заголовку; ниже — брони выбранного пассажира. Брони берутся
из `BookingService.bookingsByPassenger()` — `Map<ID пассажира, List<Booking>>` за один запрос. Удалить можно
только пассажира без броней (`PassengerService.requireDeletable`), перед удалением — подтверждение.

### Запуск вручную без лаунчера

```bash
cd console-app
docker compose run --rm app
```

Другие команды (из каталога `console-app`):

```bash
docker compose down                      # остановить базу, данные сохраняются
docker compose down -v                   # удалить базу вместе с данными
docker compose --profile app down -v     # удалить всё, включая кэш Maven

# вернуть демо-данные, не удаляя базу
docker compose exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sql/schema.sql
docker compose exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sql/data.sql
```

## Демонстрационные данные

`sql/data.sql` заполняет базу 5 пассажирами, 5 рейсами и 10 бронированиями в статусах
`CREATED`, `PAID`, `CHECKED_IN`, `COMPLETED`, `CANCELLED` — достаточно, чтобы показать все
способы поиска, фильтрации и сортировки.

## Сдача

- исходный код Java-проекта — `console-app/src/main/java`;
- `console-app/pom.xml`;
- SQL-скрипт создания базы данных — `console-app/sql/schema.sql` (+ демо-данные `sql/data.sql`);
- ER-диаграмма базы данных — `docs/er-diagram.svg`;
- экспортированный Excel-файл — создаётся пунктом меню «Экспорт в Excel»;
- инструкция по запуску — раздел «Запуск» выше.
