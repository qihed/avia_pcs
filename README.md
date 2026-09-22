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

1. Создать PostgreSQL-базу `avia_booking`.
2. Выполнить `console-app/sql/schema.sql`, затем `console-app/sql/data.sql`.
3. При необходимости задать переменные окружения:
   - `DB_URL` (по умолчанию `jdbc:postgresql://localhost:5432/avia_booking`)
   - `DB_USER` (по умолчанию `avia_app`)
   - `DB_PASSWORD` (по умолчанию `avia_app_password`)
4. Запустить из каталога `console-app`:

```bash
mvn compile exec:java
```

Экспорт создаётся в `console-app/exports/bookings.xlsx`.

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
