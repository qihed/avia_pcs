# ИС «АВИА-БРОНЬ» — бронирование авиабилетов

Консольная информационная система на Java 21 + PostgreSQL 16 (контрольная работа № 1,
«Программирование корпоративных систем», РТУ МИРЭА). Шифр ТЗ: ИС АВИА-БРОНЬ.КР1.ТЗ 01-2026.

Основная сущность — **бронирование** (`bookings`), связанные сущности — **пассажиры** (`passengers`)
и **рейсы** (`flights`). Архитектура: `ui → service → repository (JDBC) → PostgreSQL`.
Между экранами и сервисами передаются только DTO (record-классы пакета `dto`), схему БД создаёт Flyway.

Самый короткий путь до работающего меню описан в [docs/QUICKSTART.md](docs/QUICKSTART.md).

## Что реализовано

| Требование ТЗ | Где в коде (`console-app/src/main/java/ru/mirea/avia/`) |
|---|---|
| Интерактивное меню, возврат в текущее меню (FR-01, п. 6.2) | `ui/Menu`, `ui/MainView` и панели `ui/*Pane` |
| 8 операций над бронью (FR-05…FR-14) | `ui/BookingsPane`, `ui/BookingDialog`, `service/BookingService` |
| Смена статуса, отмена, аннулирование (FR-09, FR-10, FR-21) | `domain/BookingStatus.canTransitionTo()`, `service/BookingValidator`, `BookingService` |
| Расчёт цены, генерация PNR (FR-19, FR-20) | `service/FareCalculator`, `service/PnrGenerator` |
| 10 бизнес-правил BR-01…BR-10 | `service/BookingValidator`, `BookingService`, `FlightService`, `PassengerService` |
| 6 способов поиска, 5 фильтров, 5 сортировок (FR-12…FR-14) | `service/BookingSearchService`, пакет `filter`, `ui/SearchPane`, `ui/FilterSortPane` |
| Умный фильтр — строка запроса (дополнительно) | `filter/SmartFilterParser` → `filter/BookingFilter` → `filter/BookingSpecifications` |
| 11 показателей статистики (FR-15) | `service/StatisticsService`, `dto/AnalyticsDtos`, `ui/StatisticsPane` |
| Экспорт в .xlsx (4 листа) и .csv (FR-16, FR-17) | `export/Exporter`, `ExcelExporter`, `CsvExporter`, `service/ExportService`, `ui/ExportPane` |
| Справочники пассажиров и рейсов (FR-03, FR-04) | `ui/PassengersPane`, `ui/FlightsPane`, `PassengerService`, `FlightService` |
| Вывод таблиц БД постранично (FR-18) | `ui/TablesPane`, `service/TableViewService`, `repository/impl/TableViewRepositoryImpl` |
| Обработка ошибок E-101…E-601 (FR-02) | пакет `error` (`ConsoleErrorHandler`), `ui/InputPrompt`, `ui/CommandRunner` |
| JDBC: PreparedStatement, try-with-resources, транзакции (DB-01…DB-07) | `repository/impl/AbstractJdbcRepository`, `jdbc/DatabaseManager`, `jdbc/Transactions` |
| PK, FK, NOT NULL, UNIQUE, CHECK, частичные индексы | `db/migration/V1__initial_schema.sql` (= `console-app/sql/schema.sql`) |
| Тестовые данные: 6 пассажиров, 8 рейсов, 14 броней, 6 статусов | `db/demo/V2__demo_data.sql` (= `console-app/sql/data.sql`) |
| Пароли только в `.env` (NFR-11) | `config/AppProperties`, `console-app/.env.example`, `init-env.sh` / `init-env.ps1` |

Модульные тесты (JUnit 5 + AssertJ) лежат в `console-app/src/test/java`; сервисы проверяются
с репозиториями в памяти (пакет `support`). Запуск: `./mvnw test` в каталоге `console-app`.

## 1. Требования к окружению

| | |
|---|---|
| JDK | **Java 21** (`java -version` показывает `21`) |
| Сборка | Maven Wrapper в `console-app` (`./mvnw`, `mvnw.cmd`, Maven 3.9.11 скачивается сам) — ставить Maven не нужно |
| СУБД | PostgreSQL 16 в Docker (Docker Desktop или Docker Engine с Compose v2) либо установленный PostgreSQL 16 |
| Python 3 | необязательно: `scripts/verify.py`, `scripts/package_release.py` |
| Кодировка | UTF-8 в консоли и в БД |

## 2. Структура репозитория

```
avia-booking/
├── .github/workflows/ci.yml     CI: сборка и тесты, verify.py, сквозной прогон с PostgreSQL
├── docs/                        QUICKSTART, DEFENSE, TEST_CASES, ER-диаграмма (.md/.svg/.png)
├── examples/
│   ├── demo-session.txt         ввод для сквозного прогона без клавиатуры (CI)
│   └── smart-filter-queries.txt примеры строк умного фильтра
├── scripts/
│   ├── verify.py                структурная проверка проекта → VERIFICATION.md, SOURCE_MANIFEST.sha256
│   ├── build-all.sh / .ps1      ./mvnw clean verify + verify.py --build
│   ├── backup-postgres.sh       pg_dump из контейнера db → backups/
│   ├── restore-postgres.sh      восстановление дампа (с проверкой .sha256)
│   └── package_release.py       ZIP-архив проекта без target, .env, логов и выгрузок
└── console-app/                 Maven-модуль консольного приложения
    ├── pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
    ├── .env.example             шаблон локальных параметров (.env в git не хранится)
    ├── docker-compose.yml       PostgreSQL 16 (+ профиль app для запуска без JDK)
    ├── init-env.sh|ps1          создаёт .env со случайным паролем
    ├── start-compose.sh|ps1     поднимает PostgreSQL и ждёт healthcheck
    ├── stop-compose.sh|ps1      останавливает контейнеры, данные сохраняются
    ├── verify-compose.sh|ps1    показывает активный compose-файл
    ├── run.sh|ps1               компилирует и запускает приложение
    ├── reset-demo.sh|ps1        удаляет схему avia — при следующем запуске демо-данные создаются заново
    ├── sql/
    │   ├── schema.sql           та же схема, что V1, для ручного запуска через psql
    │   ├── data.sql             те же демо-данные, что V2
    │   ├── statistics_check.sql контрольные запросы TC-16
    │   └── integrity_check.sql  проверка ограничений целостности
    └── src/
        ├── main/java/ru/mirea/avia/   исходный код (пакеты — ниже)
        ├── main/resources/
        │   ├── application.properties  параметры с плейсхолдерами ${VAR:default}
        │   ├── simplelogger.properties журнал в файл avia-booking.log
        │   ├── db/migration/V1__initial_schema.sql
        │   └── db/demo/V2__demo_data.sql
        └── test/java/ru/mirea/avia/   модульные тесты
```

## 3. Быстрый старт

macOS / Linux:

```bash
cd console-app
./init-env.sh          # 1. создаёт .env со случайным паролем PostgreSQL
./start-compose.sh     # 2. поднимает PostgreSQL 16 и ждёт healthcheck
./run.sh               # 3. компилирует и запускает консольное приложение
```

Windows (PowerShell):

```powershell
cd console-app
.\init-env.ps1
.\start-compose.ps1
.\run.ps1              # переключает консоль на UTF-8
```

Запуск собранного jar (из каталога `console-app`, чтобы приложение нашло `.env`):

```bash
./mvnw clean package                 # тесты + target/avia-booking.jar со всеми зависимостями
java -jar target/avia-booking.jar    # Windows: chcp 65001, затем java -jar target\avia-booking.jar
```

Из IntelliJ IDEA: открыть `console-app/pom.xml` как проект, главный класс `ru.mirea.avia.AviaBookingLauncher`,
рабочий каталог — `console-app` (там лежит `.env`), VM options `-Dfile.encoding=UTF-8`.

Порт, запуск без JDK (`docker compose --profile app run --rm app`), резервные копии и решение типичных
проблем со скриптами — в [docs/QUICKSTART.md](docs/QUICKSTART.md).

## 4. Настройка подключения

Пароль БД хранится **только** в `console-app/.env` (NFR-11). Файл создаёт `init-env.sh` / `init-env.ps1`
(криптостойкий случайный пароль, права 600) либо вручную копированием `.env.example` с заменой `REPLACE_ME`.
`.env` и его варианты перечислены в `.gitignore`; в git лежит только шаблон:

```properties
POSTGRES_DB=avia_booking
POSTGRES_USER=avia_app
POSTGRES_PASSWORD=REPLACE_ME
POSTGRES_PORT=5432
DEMO_DATA=true
EXPORT_DIR=exports
APP_TIME_ZONE=Europe/Moscow
```

Тот же `.env` читает `docker compose` (создание базы) и приложение (подключение).
Файл `src/main/resources/application.properties` секретов не содержит — только плейсхолдеры `${VAR:default}`:

```properties
app.db.url=${DB_URL:jdbc:postgresql://localhost:${POSTGRES_PORT:5432}/${POSTGRES_DB:avia_booking}?currentSchema=avia}
app.db.user=${DB_USER:${POSTGRES_USER:avia_app}}
app.db.password=${DB_PASSWORD:${POSTGRES_PASSWORD:}}
app.db.connect-timeout-seconds=5
app.db.demo-data=${DEMO_DATA:true}
app.booking.sale-close-minutes=60
app.booking.cancel-hours=2
app.booking.expire-minutes=30
app.export.directory=${EXPORT_DIR:exports}
app.time-zone=${APP_TIME_ZONE:Europe/Moscow}
app.log-file=avia-booking.log
```

Значение переменной `VAR` ищется по порядку (`config/AppProperties`):

1. системное свойство `-DVAR=…` (`java -DDB_URL=… -jar …`);
2. переменная окружения `VAR`;
3. файл `.env` в каталоге запуска;
4. значение по умолчанию после двоеточия.

Сервисы получают готовый record `AppProperties` и к `System.getenv` не обращаются. Если параметр
некорректен, приложение выводит `Ошибка конфигурации: …` и подсказку про `.env`.
Полная таблица переменных (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `DEMO_DATA`, `EXPORT_DIR`, `APP_TIME_ZONE`) —
в [docs/QUICKSTART.md](docs/QUICKSTART.md#параметры).

## 5. База данных

### Flyway (основной способ)

При каждом старте `jdbc/MigrationRunner` применяет новые миграции к схеме `avia`:

| Файл | Что делает | Когда применяется |
|---|---|---|
| `db/migration/V1__initial_schema.sql` | таблицы, PK, FK, UNIQUE, CHECK, частичные индексы | всегда |
| `db/demo/V2__demo_data.sql` | 6 пассажиров, 8 рейсов, 14 броней | только при `DEMO_DATA=true` |

Применённую миграцию не редактируют: изменение схемы — новой версией `V3`, `V4`, …
История хранится в таблице `avia.flyway_schema_history`.

> **Даты рейсов в демо-данных считаются от дня загрузки** (`CURRENT_DATE`), а не берутся из ТЗ
> буквально: иначе к защите все рейсы окажутся в прошлом и BR-01 не даст оформить ни одной брони.
> Три брони в статусе «Создано» через 30 минут после загрузки станут «Просрочено» (BR-09) —
> **перед демонстрацией сбросьте данные.**

### Сброс демонстрационных данных

```bash
cd console-app
./reset-demo.sh        # Windows: .\reset-demo.ps1
```

Скрипт просит ввести `RESET` и выполняет `DROP SCHEMA IF EXISTS avia CASCADE` через `psql` внутри
контейнера `db` (локальный клиент PostgreSQL не нужен). Приложение перед этим нужно закрыть.
При следующем запуске Flyway заново создаёт схему (V1) и загружает демо-данные (V2).

### Ручной вариант через psql

Для установленного PostgreSQL без Docker:

```bash
# ICU-локаль нужна, чтобы поиск по фамилии без учёта регистра работал на кириллице
createdb -U postgres -T template0 -E UTF8 --locale-provider=icu --icu-locale=ru-RU avia_booking
psql -U postgres -d avia_booking -f console-app/sql/schema.sql   # DROP/CREATE SCHEMA avia + объекты V1
psql -U postgres -d avia_booking -f console-app/sql/data.sql     # те же данные, что V2
psql -U postgres -d avia_booking -c "SELECT count(*) FROM avia.bookings;"   # 14
```

Параметры подключения в этом случае задаются в `console-app/.env` (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
Увидев непустую схему без истории миграций, Flyway фиксирует её как базовую версию 2
(`baselineOnMigrate`, `baselineVersion = 2`) и объекты повторно не создаёт.
`scripts/verify.py` следит, чтобы `sql/schema.sql` совпадал с V1, а `sql/data.sql` — с V2.

Контрольные запросы для статистики и проверка ограничений целостности (для Docker — через `psql` в контейнере):

```bash
cd console-app
docker compose exec -T db psql -U avia_app -d avia_booking < sql/statistics_check.sql
docker compose exec -T db psql -U avia_app -d avia_booking < sql/integrity_check.sql
```

## 6. Сборка, тесты и проверки

```bash
cd console-app
./mvnw clean package       # компиляция, тесты, target/avia-booking.jar (maven-shade-plugin)
./mvnw test                # только модульные тесты (JUnit 5 + AssertJ)
./mvnw -q compile exec:java   # запуск без сборки jar (так работает run.sh)
```

Из корня репозитория:

```bash
python3 scripts/verify.py           # статические проверки, отчёт VERIFICATION.md
python3 scripts/verify.py --build   # плюс ./mvnw clean verify и docker compose config
scripts/build-all.sh                # Windows: scripts\build-all.ps1 — то же, что два шага выше
python3 scripts/package_release.py  # ZIP рядом с каталогом проекта + .sha256
```

`scripts/verify.py` проверяет: наличие обязательных файлов; число Java-файлов и JavaDoc; отсутствие
Spring/JPA/JavaFX/Hibernate/Lombok; SQL-литералы только в `repository/impl`; `System.out`/`System.in`/`Scanner`
только в `ui`; отсутствие конкатенации в `prepareStatement(…)` и вызовов `createStatement(`; отсутствие
паролей в коде, `application.properties`, compose-файле и `.env.example`; отсутствие TODO/FIXME;
класс ≤ 300 строк, метод ≤ 40 строк; наличие всех кодов ошибок в `ErrorCode`; совпадение
`sql/schema.sql` с V1 и `sql/data.sql` с V2; маркер и порт в `docker-compose.yml`; известные форматы секретов.
Результат — таблица PASS/FAIL/SKIP в `VERIFICATION.md` и контрольные суммы исходников в
`SOURCE_MANIFEST.sha256`; при любом FAIL код возврата 1. Логи шагов `--build` — в `build-reports/`.

CI (`.github/workflows/ci.yml`) запускается на каждый push и pull request:

| Задание | Что делает |
|---|---|
| `app` | JDK 21 → `./mvnw -B -ntp clean verify` → `python3 scripts/verify.py` → jar как артефакт `avia-booking-console-jar` |
| `smoke` | сервис `postgres:16-alpine` → сборка jar → `java -jar target/avia-booking.jar < ../examples/demo-session.txt` → в выводе должны быть `Пассажиров: 6`, `102 390,00` и `До свидания` |

## 7. Проверка работоспособности

1. После запуска выводится памятка `AVIA BOOKING CONSOLE IS READY` (адрес БД, пользователь, каталог выгрузки,
   файл журнала; пароль не печатается), затем главное меню и строка
   `Пассажиров: 6 · Рейсов: 8 · Активных броней: 12`.
2. Пункт «3 → 2» выводит список из 14 бронирований.
3. Пункт «6» выводит статистику; суммарная выручка — 102 390,00 руб.
4. Пункт «7 → 1» создаёт файл `.xlsx` в каталоге `console-app/exports`.
5. Пункт «0» завершает работу: `Соединение с базой данных закрыто. До свидания!`

Полный сценарий проверки TC-01…TC-16 — в [docs/TEST_CASES.md](docs/TEST_CASES.md).

## 8. Типичные проблемы

| Симптом | Причина и решение |
|---|---|
| `Операция не выполнена [E-501]: Нет связи с базой данных…` | Контейнер не запущен или неверные параметры в `.env`. Проверить `docker compose ps` в `console-app`, выбрать «1. Повторить попытку» |
| `Ошибка конфигурации: …` при старте | Некорректное значение в `.env` или `-D…` (например, `DB_URL` не начинается с `jdbc:postgresql://`). Сверить с `.env.example` |
| Порт 5432 занят | `./init-env.sh --port 5440 --force` (Windows: `.\init-env.ps1 -PostgresPort 5440 -Force`), подробности — в QUICKSTART |
| Кириллица выводится знаками вопроса | Консоль Windows не в UTF-8. Запускать через `.\run.ps1` или выполнить `chcp 65001` |
| `relation "bookings" does not exist` | В `DB_URL` нет параметра `currentSchema=avia` |
| `java -version` показывает не 21 | Указать `JAVA_HOME` на JDK 21 — `mvnw` использует именно его |
| Поиск по фамилии находит только при точном регистре | База создана вручную с локалью `C`. Пересоздать с ICU-локалью (раздел 5) |
| Ни один рейс не доступен для продажи | Демо-данные загружены давно, рейсы ушли в прошлое — `./reset-demo.sh` и перезапуск |
| Не создаётся файл экспорта (E-601) | Нет прав на запись в каталог `EXPORT_DIR` (каталог создаётся автоматически) |

Технические подробности ошибок (stack trace, SQLState) пишутся в журнал `avia-booking.log` в каталоге запуска
(для `run.sh` — `console-app/avia-booking.log`), в консоль они не выводятся.

## Пакеты

Пути от `console-app/src/main/java/ru/mirea/avia/`. В корне пакета — `AviaBookingLauncher` (точка входа)
и `AviaBookingConsoleApplication` (composition root: создаёт репозитории → сервисы → экраны).

| Пакет | Назначение | Основные классы |
|---|---|---|
| `config` | Конфигурация и стартовая памятка | `AppProperties` (record с `Db`, `BookingRules`, `Export`), `StartupConnectionInfoLogger` |
| `jdbc` | Соединение, транзакции, миграции | `DatabaseManager`, `Transactions`, `MigrationRunner`, `SqlErrors` |
| `domain` | Сущности и перечисления предметной области | `Passenger`, `Flight`, `Booking` (← `AuditedEntity`), `BookingStatus`, `FareClass`, `FlightStatus`, `DocumentType` |
| `dto` | Контракты между `ui` и `service` | `PassengerDtos`, `FlightDtos`, `BookingDtos`, `AnalyticsDtos`, `ExportDtos`, `TableDtos`, `PageResponse`, `ErrorReport` |
| `error` | Коды и иерархия исключений, обработчик ошибок | `ErrorCode`, `AppException` и наследники, `ConsoleErrorHandler` |
| `filter` | Критерии отбора и сортировки броней | `BookingFilter`, `BookingSort`, `SmartFilterParser`, `BookingSpecifications` |
| `repository` | Интерфейсы доступа к данным | `CrudRepository<T, ID>`, `PassengerRepository`, `FlightRepository`, `BookingRepository`, `TableViewRepository` |
| `repository/impl` | JDBC-реализации — единственное место с SQL | `AbstractJdbcRepository`, `PassengerRepositoryImpl`, `FlightRepositoryImpl`, `BookingRepositoryImpl`, `TableViewRepositoryImpl` |
| `service` | Бизнес-правила, транзакции, DTO | `BookingService`, `BookingValidator`, `FareCalculator`, `PnrGenerator`, `BookingSearchService`, `PassengerService`, `PassengerValidator`, `FlightService`, `FlightValidator`, `StatisticsService`, `ExportService`, `TableViewService`, мапперы `PassengerMapper`, `FlightMapper`, `BookingMapper` |
| `export` | Выгрузка в файлы | `Exporter` ← `AbstractFileExporter` ← `ExcelExporter` (+ `ExcelStyles`, `StatisticsSheetWriter`), `CsvExporter` |
| `ui` | Меню, ввод, вывод — единственное место с консолью | `MainView`, `Menu`, `CommandRunner`, `ConsoleIo`, `InputPrompt`, `Ui`, `Cards`, панели `*Pane`, диалоги `*Dialog` |
| `util` | Форматирование без побочных эффектов | `DateTimeFormat`, `MoneyFormat`, `TextTable`, `UiText` |

## Соответствие классов из ТЗ и кода

Названия из ТЗ и прежней версии проекта соответствуют новым классам так:

| В ТЗ / прежней версии | Сейчас |
|---|---|
| `Main` | `AviaBookingLauncher` (точка входа) + `AviaBookingConsoleApplication` (сборка и запуск) |
| `ConsoleApp`, `MainMenu` | `ui/MainView` |
| `BookingMenu` (+ `BookingCreateDialog`) | `ui/BookingsPane` + `ui/BookingDialog` |
| `SearchMenu` | `ui/SearchPane` |
| `FilterSortMenu` | `ui/FilterSortPane` (+ умный фильтр) |
| `StatisticsMenu` | `ui/StatisticsPane` |
| `ExportMenu` | `ui/ExportPane` |
| `PassengerMenu` (+ `PassengerForm`) | `ui/PassengersPane` + `ui/PassengerDialog` |
| `FlightMenu` (+ `FlightForm`) | `ui/FlightsPane` + `ui/FlightDialog` |
| вывод таблиц БД (`DatabaseTablesMenu`) | `ui/TablesPane` |
| `InputReader` | `ui/InputPrompt` |
| `TablePrinter` | `util/TextTable` + `ui/Ui.table` |
| `Menu` / `MenuItem` | `ui/Menu` (построитель) + `ui/CommandRunner` |
| пакет `model` | пакет `domain` |
| пакет `exception` | пакет `error` (+ `ConsoleErrorHandler`, `dto/ErrorReport`) |
| `util/DatabaseManager`, `TransactionRunner` | `jdbc/DatabaseManager` (+ `jdbc/Transactions`, `MigrationRunner`, `SqlErrors`) |
| `ConfigLoader`, `AppConfig` | `config/AppProperties` |
| `PricingService` | `service/FareCalculator` |
| `DatabaseViewService` | `service/TableViewService` |
| `Statistics` | `dto/AnalyticsDtos.StatisticsResponse` |
| `PriceQuote`, `FlightSeats`, `DatabaseTable`, `TablePage` | `dto/BookingDtos.PriceQuote`, `dto/FlightDtos.FlightAvailability`, `dto/TableDtos.DatabaseTable`, `dto/TableDtos.TablePage` |
| `BookingSort` (в `service`) | `filter/BookingSort` |
| `DateUtils`, `MoneyUtils` | `util/DateTimeFormat`, `util/MoneyFormat` |
| `Views` | `ui/Cards` + `util/UiText` |

## Коды ошибок

| Код | Ситуация | Исключение |
|---|---|---|
| E-101 | Текст вместо числа | `ValidationException` |
| E-102 | Некорректная дата или время | `ValidationException` |
| E-103 | Нарушен формат поля (e-mail, место, PNR, сумма, строка умного фильтра) | `ValidationException` |
| E-104 | Пустое обязательное поле | `ValidationException` |
| E-105 | Несуществующий пункт меню | `ValidationException` / сообщение меню |
| E-201 | Бронь с указанным ID/PNR не найдена | `EntityNotFoundException` |
| E-202 | Пассажир или рейс не найден | `EntityNotFoundException` |
| E-300 | Рейс закрыт для продажи (BR-01) | `BusinessRuleException` |
| E-301 | Место занято / мест нет (BR-02, BR-03) | `SeatUnavailableException` |
| E-302 | Запрещённый переход статуса (BR-07) | `InvalidStatusTransitionException` |
| E-303 | Дубль брони пассажира на рейс (BR-04) | `DuplicateBookingException` |
| E-304 | Отмена позже чем за 2 часа (BR-08) | `BusinessRuleException` |
| E-305 | Удаление активной брони / пассажира или рейса с бронями (BR-10) | `BusinessRuleException` |
| E-306 | *Дополнительно:* операция недоступна в текущем состоянии (изменение после регистрации, регистрация раньше чем за 24 ч, завершение до прилёта, уменьшение вместимости ниже проданных мест, отмена рейса с зарегистрированными пассажирами) | `BusinessRuleException` |
| E-307 | *Дополнительно:* дубликат документа / e-mail пассажира или рейса с тем же номером и временем | `BusinessRuleException` |
| E-500 | *Дополнительно:* непредвиденная внутренняя ошибка (любое исключение, не входящее в иерархию `AppException`) | — (обрабатывает `ConsoleErrorHandler`) |
| E-501 | Нет соединения с БД | `DataAccessException` |
| E-502 | Ошибка выполнения SQL | `DataAccessException` |
| E-503 | Нарушено ограничение целостности СУБД | `DataAccessException` |
| E-601 | Не удалось записать файл экспорта | `ExportException` |

Ошибка команды выводится строкой `Операция не выполнена [E-xxx]: текст`. Ошибка ввода в диалоге не прерывает
операцию: поле запрашивается повторно (`Ошибка: … Повторите ввод.`, для занятого места — `… Выберите другое место.`).

## Решения, уточняющие ТЗ

- **«Активная» бронь** в счётчиках, статистике (ST-08) и частичных индексах — любая, кроме `CANCELLED`
  и `EXPIRED` (так получаются 12 активных броней и загрузка 0,9 %, как в ТЗ). Фильтр FL-01
  «Только активные» по ТЗ отбирает `CREATED`, `PAID`, `CHECKED_IN`.
- **Смена статуса (FR-09):** в диалоге предлагаются только допустимые переходы, но можно ввести код
  статуса вручную (например, `PAID`) — так показывается TC-08 «переход COMPLETED -> PAID недопустим».
  `EXPIRED` вручную не ставится — только правилом BR-09.
- **Временные условия переходов** (рис. 5.3): оплата — до вылета, регистрация — не раньше чем за 24 часа
  до вылета, «Перелёт выполнен» — после прилёта. Бронь на рейс, отменённый авиакомпанией, можно отменить
  в любой момент.
- **Изменение брони (FR-08)** доступно в статусах «Создано» и «Оплачено», пока рейс открыт для продажи.
- **Рейсы:** рейс со статусом «Вылетел»/«Прибыл» считается вылетевшим независимо от времени; рейс, на который
  уже зарегистрированы пассажиры, отменить нельзя (их брони по матрице BR-07 уже не отменяются); вместимость
  нельзя уменьшить так, чтобы проданные места оказались вне салона. Номер ряда — не больше 99 (CON-09).
- **PNR** всегда содержит хотя бы одну букву, чтобы не путаться с числовым ID брони.
- **Номер документа маскируется** везде, кроме детальных карточек (NFR-09), в том числе при выводе
  таблиц БД и в файлах экспорта.
- **BR-09** выполняется при старте, при каждом показе главного меню (счётчик активных броней), в разделе
  «Бронирования» перед созданием, выводом списка, поиском по ID/PNR, изменением, сменой статуса и отменой,
  а также перед фильтрацией, статистикой и экспортом.
- **Фильтрация:** каждый пункт раздела 5 заменяет только свой критерий, поэтому фильтры по разным полям
  накапливаются; при входе в раздел выборка сбрасывается. После каждого изменения выборка перечитывается из БД.
- **Умный фильтр** (пункт «5 → 7»): строка вида `статус:оплачено класс:бизнес цена>=15000` разбирается
  `SmartFilterParser` в `BookingFilter` и применяется предикатами Stream API (`BookingSpecifications`);
  в SQL строка не попадает. Строка задаёт выборку целиком (заменяет прежние фильтры), сортировка сохраняется.
  Примеры — [examples/smart-filter-queries.txt](examples/smart-filter-queries.txt).
- **DTO-контракты:** публичные методы сервисов принимают и возвращают record-классы пакета `dto`
  (`BookingRequest`, `BookingResponse`, `PriceQuote`, `StatisticsResponse`…); доменные объекты за пределы
  слоя сервисов не выходят. Исключение — интерфейс `Exporter`, который получает `List<Booking>` от `ExportService`.
- **Обработка ошибок и correlation ID:** каждая команда меню выполняется через `CommandRunner`, который
  создаёт для неё correlation ID; `ConsoleErrorHandler` превращает исключение в `ErrorReport` — оператор видит
  код и русский текст, stack trace и SQLState пишутся только в журнал. Для E-5xx к тексту добавляется
  короткий correlation ID — по нему администратор находит строку с подробностями в `avia-booking.log`.
  ID хранится в `error/CorrelationId` (`ThreadLocal`), потому что `slf4j-simple` не поддерживает MDC.
- **Журнал:** SLF4J (`slf4j-simple`) пишет в файл `avia-booking.log` в каталоге запуска
  (`simplelogger.properties`); журнал Apache POI перенаправлен в SLF4J (`log4j-to-slf4j`).
  Логируют только пакеты `config` и `error`.
- **Соединение с БД** открывается на время одной транзакции (`DatabaseManager.read/write`) и сразу закрывается;
  между командами меню открытых соединений нет.
- **Java 21:** record-классы, `switch` с сопоставлением по типу, text blocks для SQL, `RandomGenerator`.
- **Flyway** владеет схемой (V1), демо-данные отделены (V2 в `db/demo`, флаг `DEMO_DATA`); ручная загрузка
  через `psql` фиксируется как базовая версия 2.
- Все времена приложения считаются в часовом поясе `Europe/Moscow` (CON-06, `APP_TIME_ZONE`);
  приложение устанавливает этот пояс и для JVM, чтобы драйвер одинаково читал `TIMESTAMP`.

## Команда и работа с Git

| Участник | Фича | Ветка |
|---|---|---|
| Даня Майоров | Создание бронирования: FR-05, FR-19, FR-20, BR-01…BR-06 (`BookingDialog.create`, `BookingService.create`, `FareCalculator`, `PnrGenerator`, `BookingValidator`) | `feature/booking-create` |
| Даня Скарлат | Управление бронью: FR-06…FR-11, FR-21 (`BookingsPane`, `BookingService` — update/changeStatus/cancel/delete/expireOutdated, `BookingStatus`, `BookingMapper`) | `feature/booking-management` |
| Темир | Поиск, фильтрация, сортировка: FR-12…FR-14 (`SearchPane`, `FilterSortPane`, `BookingSearchService`, пакет `filter`: `BookingSort`, `BookingFilter`, `SmartFilterParser`, `BookingSpecifications`) | `feature/search-filter-sort` |
| Катя | Статистика и экспорт: FR-15…FR-17 (`StatisticsPane`, `ExportPane`, `StatisticsService`, `ExportService`, `AnalyticsDtos`, пакет `export`) | `feature/statistics-export` |

Общие файлы (`Booking.java`, `BookingService.java`, `BookingRepository*.java`, `BookingDtos.java`,
`BookingsPane.java`, `AviaBookingConsoleApplication.java`, миграции Flyway и `console-app/sql/*.sql`) меняются
только после сообщения команде; схема меняется только новой миграцией. Готовая фича вливается в `main`
через Pull/Merge Request; после каждого merge — `./mvnw clean verify` в `console-app`,
`python3 scripts/verify.py` и ручная проверка запуска (CI делает то же автоматически).

Подготовка к защите (кто что объясняет, где что лежит в коде) — [docs/DEFENSE.md](docs/DEFENSE.md).
