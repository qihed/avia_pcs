# Подготовка к защите

Каждый участник должен уметь пройти **любую** фичу по цепочке
«меню → service → repository/impl → SQL → таблицы БД» (распределение задач, раздел 3).
Ниже — маршруты по коду для всех четырёх фич и ответы на обязательные вопросы (п. 12.2 ТЗ).

Предлагаемый обмен: Катя объясняет свою фичу Дане Скарлату, Даня Скарлат — Темиру,
Темир — Дане Майорову, Даня Майоров — Кате.

Пути ниже указаны от `console-app/src/main/java/ru/mirea/avia/`, номера строк соответствуют текущему коду.
Запись `:62` без имени файла означает строку 62 файла, названного ближе всего перед ней в той же схеме или фразе;
в таблицах — файла из заголовка столбца.

Общий принцип слоёв: экран (`ui`) собирает ввод в **запрос-DTO**, сервис (`service`) открывает транзакцию,
проверяет правила и вызывает репозиторий (`repository/impl`), репозиторий выполняет SQL через
`PreparedStatement`, сервис превращает доменный объект в **ответ-DTO**, экран его печатает.

---

## 1. Создание бронирования — Даня Майоров (FR-05, FR-19, FR-20, BR-01…BR-06)

```
MainView «3. Бронирования»                                    ui/MainView.java:60
 └─ BookingsPane «1. Создать бронирование» → create()          ui/BookingsPane.java:52, :62
     └─ BookingDialog.create()                                 ui/BookingDialog.java:38
         ├─ BookingService.expireOutdated()         BR-09      service/BookingService.java:195
         ├─ askPassenger() → PassengerService.get()  E-202      ui/BookingDialog.java:90, service/PassengerService.java:51
         │    └─ PassengerRepositoryImpl.findById()             repository/impl/PassengerRepositoryImpl.java:55
         │         SELECT … FROM passengers WHERE id = ?
         ├─ FlightService.listOpenForSale()         BR-01, BR-02   service/FlightService.java:65
         │    ├─ FlightRepositoryImpl.findAll()                 repository/impl/FlightRepositoryImpl.java:62
         │    │    SELECT … FROM flights ORDER BY departure_time, id
         │    └─ BookingRepositoryImpl.countActiveGroupByFlightId()   repository/impl/BookingRepositoryImpl.java:170
         │         SELECT flight_id, count(*) FROM bookings WHERE status NOT IN (?, ?) GROUP BY flight_id
         ├─ selectFlight() → BookingService.checkFlightAvailable()    ui/BookingDialog.java:140, service/BookingService.java:85
         │    ├─ BookingValidator.checkOpenForSale()  BR-01 → E-300   service/BookingValidator.java:77
         │    ├─ BookingValidator.checkCapacity()     BR-02 → E-301   :102
         │    │    └─ BookingRepositoryImpl.findActiveByFlightId()    repository/impl/BookingRepositoryImpl.java:157
         │    └─ BookingValidator.checkNoDuplicate()  BR-04 → E-303   service/BookingValidator.java:121
         │         └─ BookingRepositoryImpl.findActiveByFlightIdAndPassengerId()   repository/impl/BookingRepositoryImpl.java:163
         ├─ BookingService.checkSeatAvailable()                ui/BookingDialog.java:57, service/BookingService.java:93
         │    ├─ normalizeSeat()     формат «12A»  E-103        service/BookingValidator.java:52
         │    ├─ checkSeatInCabin()  ряд существует E-103       :94
         │    └─ checkSeatFree()     BR-03 → E-301              :109
         ├─ askBaggage()  бизнес-класс — багаж уже в тарифе     ui/BookingDialog.java:108
         ├─ confirm() → BookingService.quote() → FareCalculator.calculate()   BR-06
         │                                                     ui/BookingDialog.java:123, service/BookingService.java:102,
         │                                                     service/FareCalculator.java:17
         └─ (подтверждено) BookingService.create(BookingRequest)   service/BookingService.java:66
              ├─ expireOutdated() — отдельной транзакцией до записи   :68
              └─ transactions.write(…) — все проверки повторяются, затем   :69
                   ├─ generateUniqueRef() → PnrGenerator.generate() + existsByBookingRef()   FR-20
                   │                                           service/BookingService.java:256, service/PnrGenerator.java:28
                   ├─ new Booking(…) статус CREATED, booking.onCreate(now)   domain/Booking.java:42, service/BookingService.java:79
                   ├─ BookingRepositoryImpl.insert()            repository/impl/BookingRepositoryImpl.java:89
                   │    └─ AbstractJdbcRepository.insertAndGetId()   repository/impl/AbstractJdbcRepository.java:85
                   │         INSERT INTO bookings (…) VALUES (?, …)   (драйвер добавляет RETURNING id)
                   └─ BookingMapper.toResponse() → BookingResponse   service/BookingMapper.java:20
 └─ BookingsPane.printCreated() — карточка «Бронирование создано»   ui/BookingsPane.java:168
```

**Где бизнес-правила:** только в `service` (`BookingValidator`, `BookingService`, `FareCalculator`).
Диалог в `ui` лишь вызывает проверки по шагам (`InputPrompt.read()`, ui/InputPrompt.java:44), чтобы при ошибке
переспросить только текущее поле. Поэтому TC-05 заканчивается строкой
`Место 14C на рейсе SU1420 уже занято. Выберите другое место.`, а не выходом в меню.
BR-02…BR-04 дополнительно защищены БД: `uq_bookings_flight_seat_active`,
`uq_bookings_flight_passenger_active`, внешние ключи, `chk_bookings_price_positive`.

**Что передаётся между слоями:** диалог возвращает `BookingRequest` (dto/BookingDtos.java:18) — ID пассажира
и рейса, класс, место, багаж, но **не цену**: стоимость считает система (BR-06). Сервис возвращает
`BookingResponse` (dto/BookingDtos.java:34) с вложенными `PassengerResponse` и `FlightResponse`.

**Почему транзакция:** между проверкой места и вставкой никто не должен занять то же место. Всё внутри
`transactions.write(…)` идёт через одно соединение; при любой ошибке — `rollback()` в
`DatabaseManager.execute()` (jdbc/DatabaseManager.java:75, :86). Если проверку всё же обойдут, сработает
частичный уникальный индекс, а `SqlErrors` превратит ошибку в E-503.

**Расчёт цены:** `price = base_price × k(fareClass) + baggageFee` (service/FareCalculator.java:37);
k хранится в перечислении `FareClass` (domain/FareClass.java:7: 1.0 / 1.6 / 2.5), сбор 2 500 руб.
(`FareCalculator.BAGGAGE_FEE`) не добавляется для бизнес-класса; округление `HALF_UP` до копеек, только `BigDecimal`.
В бизнес-классе признак багажа всегда `true` (`normalizeBaggage()`, service/BookingService.java:268).

---

## 2. Управление бронью — Даня Скарлат (FR-06…FR-11, FR-21, BR-07…BR-10)

| Пункт меню | UI (`ui/BookingsPane.java`) | Service (`service/BookingService.java`) | Repository / SQL (`repository/impl/BookingRepositoryImpl.java`) |
|---|---|---|---|
| 2. Список всех броней | `list()` :67 → `Ui.table(Cards.bookingTable(), …)` | `list()` :108 — сначала BR-09 | `findAll()` :104 → `SELECT … FROM bookings b JOIN passengers p … JOIN flights f … ORDER BY b.id` |
| 3. Бронь по ID / PNR | `details()` :73 → `askBooking()` :143 | `find()` :121 — число → ID, иначе PNR (формат PNR → E-103, не найдено → E-201) | `findById()` :99 / `findByBookingRef()` :128 → `… WHERE b.booking_ref = ?` |
| 4. Изменить бронь | `edit()` :80 → `BookingDialog.change()` ui/BookingDialog.java:64 | `checkCanModify()` :144 (E-306, E-300) → `update()` :152 — BR-03 для нового места, BR-06 пересчёт, `Booking.modify()` | `update()` :109 → `UPDATE bookings SET seat_number = ?, fare_class = ?, baggage_included = ?, price = ?, status = ?, updated_at = ? WHERE id = ?` |
| 5. Сменить статус | `changeStatus()` :93 → `printTransitions()` :153, `parseStatus()` :181 | `changeStatus()` :171 → проверка BR-07 → `Booking.changeStatus()` | `update()` |
| 6. Отменить бронь | `cancel()` :113 → `confirmCancel()` :147 | `checkCanCancel()` :182 (E-302 / E-304) → `cancel()` :190 = `changeStatus(id, CANCELLED)` | `update()` (status = CANCELLED) |
| 7. Удалить бронь | `delete()` :127 | `checkCanDelete()` :203 (E-305) → `delete()` :211 | `deleteById()` :118 → `DELETE FROM bookings WHERE id = ?` |
| при старте и перед операциями | `AviaBookingConsoleApplication.start()` (AviaBookingConsoleApplication.java:103) | `expireOutdated()` :195 — BR-09 | `expireCreatedBefore()` :194 → `UPDATE bookings SET status = ?, updated_at = ? WHERE status = ? AND created_at < ?` |

Правила, которые вызывает сервис, лежат в `service/BookingValidator.java`: `normalizeRef()` :62,
`checkCanModify()` :130, `checkStatusChange()` :146, `checkCanCancel()` :173, `checkCanDelete()` :196.
Доменные методы — `Booking.changeStatus()` (domain/Booking.java:72) и `Booking.modify()` (domain/Booking.java:84);
диалог изменения — `BookingDialog.change()` (ui/BookingDialog.java:64).

**Проверка до подтверждения.** Для отмены, удаления и изменения экран сначала вызывает
`checkCanCancel` / `checkCanDelete` / `checkCanModify` (ui/BookingsPane.java:149, :132, :84) и только потом
спрашивает «да/нет». Поэтому в TC-11 вопроса об удалении нет — сразу выводится E-305.
Сервис при записи всё равно проверяет правило ещё раз внутри транзакции.

**Матрица переходов (BR-07)** живёт в самом перечислении — `BookingStatus.canTransitionTo()`
(domain/BookingStatus.java:55), список допустимых статусов — `allowedTransitions()` (:65), поэтому логика
не дублируется. `BookingMapper.toResponse()` (service/BookingMapper.java:20) убирает из `allowedStatuses`
статус `EXPIRED` — его ставит только система. `Booking.changeStatus()` (domain/Booking.java:72) — единственный
способ сменить статус: поле `status` закрыто, сеттера нет, при запрещённом переходе бросается
`InvalidStatusTransitionException` (E-302).

**Временные условия** (рис. 5.3) — в `BookingValidator.checkStatusChange()` (service/BookingValidator.java:146):
оплата — до вылета; регистрация — не раньше чем за 24 ч (`validateCheckInWindow()` :221);
«Перелёт выполнен» — после прилёта (`validateArrived()` :230); отмена — не позже чем за 2 ч (BR-08, `checkCanCancel()` :173).
Бронь на рейс, отменённый авиакомпанией, отменить можно всегда.

**Как Booking собирается из ResultSet:** `BookingRepositoryImpl.map()`
(repository/impl/BookingRepositoryImpl.java:199) — один запрос с двумя JOIN (`SELECT`, :26); столбцы пассажира
и рейса идут с префиксами `p_` и `f_`, их разбирают `PassengerRepositoryImpl.map(rs, "p_")`
(repository/impl/PassengerRepositoryImpl.java:101) и `FlightRepositoryImpl.map(rs, "f_")`
(repository/impl/FlightRepositoryImpl.java:99). Метки времени восстанавливаются `restoreAudit()`.
Получается полный объект `Booking` с вложенными `Passenger` и `Flight`; экран же получает `BookingResponse`.

---

## 3. Поиск, фильтрация, сортировка — Темир (FR-12…FR-14)

**Поиск** — `SearchPane` → `BookingSearchService`:

| Код | UI (`ui/SearchPane.java`) | Service (`service/BookingSearchService.java`) | Как ищет |
|---|---|---|---|
| SR-01 PNR | `byRef()` :41 | `byRef()` :57 | SQL `WHERE b.booking_ref = ?` (формат проверяет `normalizeRef`, ввод приводится к верхнему регистру) |
| SR-02 фамилия | `byLastName()` :53 | `byLastName()` :65 (пустая строка → E-104) | SQL `WHERE LOWER(p.last_name) LIKE LOWER(?)`, параметр `%…%`; `%`, `_` и `\` из ввода экранируются (`containsPattern()` repository/impl/BookingRepositoryImpl.java:214) |
| SR-03 документ | `byDocument()` :59 | `byDocumentNumber()` :74 | SQL `WHERE p.document_number = ?` (пробелы удаляются; номер с дефисами ищется и без них) |
| SR-04 рейс | `byFlightNumber()` :65 | `byFlightNumber()` :91 | SQL `WHERE f.flight_number = ?` (номер проверяет `FlightValidator.normalizeFlightNumber()` service/FlightValidator.java:29) |
| SR-05 маршрут | `byRoute()` :71 | `byRoute()` :97 → `bookingsOfFlights()` :145 | Stream: рейсы → `Set<Long>` ID → брони из этого множества |
| SR-06 дата | `byDate()` :78 | `byDepartureDate()` :106 → `bookingsOfFlights()` :145 | Stream: то же, условие по дате вылета |

`LOWER(...) LIKE LOWER(?)` — аналог `ILIKE`, который работает и в MySQL (NFR-21).

**Фильтры** — `FilterSortPane` хранит *текущую выборку*: неизменяемый `BookingFilter` и `BookingSort`.
При входе в раздел (`show()` ui/FilterSortPane.java:66) они сбрасываются; пункты 1–5 заменяют только свой
критерий через `with…` (filter/BookingFilter.java:68 и далее), поэтому фильтры по разным полям накапливаются,
пункт 9 сбрасывает всё. После каждого изменения `apply()` (ui/FilterSortPane.java:149) → `load()`
(ui/FilterSortPane.java:154): BR-09, затем

```
BookingSearchService.search(filter, sort)                  service/BookingSearchService.java:112
 └─ transactions.read(() -> bookings.findAll().stream()
        .filter(BookingSpecifications.from(filter))        service/BookingSearchService.java:115 → filter/BookingSpecifications.java:26
        .sorted(sort.comparator())                          service/BookingSearchService.java:118 → filter/BookingSort.java:35
        .map(mapper::toResponse)
        .toList())
```

| Код | Пункт | Метод экрана (`ui/FilterSortPane.java`) | Критерий в `BookingFilter` |
|---|---|---|---|
| FL-01 | 1. Фильтр по статусу | `byStatus()` :91 | `status` или `inProgressOnly` (CREATED, PAID, CHECKED_IN — `BookingStatus.isInProgress()` domain/BookingStatus.java:45) |
| FL-02 | 2. Фильтр по классу | `byFareClass()` :103 | `fareClass` |
| FL-03 | 3. Фильтр по рейсу | `byFlight()` :109 | `flight` — числовой ID или номер (`flightCriterion()` service/BookingSearchService.java:129) |
| FL-04 | 4. Фильтр по датам вылета | `byDates()` :115 | `departureFrom`, `departureTo`; «с» позже «по» → E-102 в конструкторе record (filter/BookingFilter.java:48) |
| FL-05 | 5. Фильтр по стоимости | `byPrice()` :124 | `prices` (`withPriceRange()` filter/BookingFilter.java:95); минимум больше максимума → E-103 (`validatePrices()` filter/BookingFilter.java:103) |

**Сортировки** — перечисление `BookingSort` (filter/BookingSort.java:13): каждый элемент хранит свой
`Comparator<Booking>`: SO-01 `BY_DEPARTURE`, SO-02 `BY_PRICE_DESC`, SO-03 `BY_LAST_NAME`, SO-04 `BY_CREATED_DESC`,
SO-05 `BY_STATUS_THEN_DEPARTURE` (по умолчанию — `BY_ID`). При равенстве ключа порядок доопределяется по ID брони.
Выбор — `chooseSort()` ui/FilterSortPane.java:133.

**Умный фильтр** (пункт 9) — строка запроса вместо пяти диалогов:

```
FilterSortPane.smartFilter()                               ui/FilterSortPane.java:140
 └─ InputPrompt.read("Строка запроса", search::parse)      ui/InputPrompt.java:44 — ошибка → повторный ввод
     └─ BookingSearchService.parse()                       service/BookingSearchService.java:124
         ├─ SmartFilterParser.parse()                      filter/SmartFilterParser.java:30
         │    ├─ длина > 500 → E-103                        :25
         │    ├─ TOKEN  "([^"]*)"|(\S+) — слова и фразы в кавычках   :26
         │    ├─ PRICE  (цена|price)(<=|>=|<|>|=)сумма → PriceConstraint   :27
         │    ├─ Criteria.apply(key, value)                 :125 — ключ до первого «:»
         │    │    статус/status → status() :73, класс/class → fareClass() :85 (switch, неизвестное → E-103),
         │    │    активные/active, рейс/flight, откуда/from, куда/to, пассажир/passenger,
         │    │    с/since, по/until → DateTimeFormat.parseDate() (E-102); неизвестный ключ → свободный текст
         │    └─ Criteria.toFilter() → new BookingFilter(…)  :141 — проверка диапазонов дат и сумм
         └─ normalizeFilter()                              service/BookingSearchService.java:164
              рейс и коды IATA проверяет FlightValidator (E-103)
 └─ apply(next, sort) → search() → BookingSpecifications.from()   filter/BookingSpecifications.java:26
      список Predicate<Booking> для заданных критериев → reduce(b -> true, Predicate::and)   :45
      слово без ключа — PNR, фамилия или номер рейса содержат его (freeText() :58)
```

Строка задаёт выборку целиком (заменяет прежние фильтры), сортировка сохраняется; пустая строка снимает фильтры.
Примеры — `examples/smart-filter-queries.txt`.

---

## 4. Статистика и экспорт — Катя (FR-15…FR-17)

**Статистика:** главное меню «6» (ui/MainView.java:63) → `StatisticsPane.show()` (ui/StatisticsPane.java:38) →
`BookingService.expireOutdated()` (BR-09) → `StatisticsService.calculate()` (service/StatisticsService.java:52) →
одной транзакцией `passengers.count()`, `flights.findAll()`, `bookings.findAll()` (:53) → `summarize()` (:56) → Stream API:

| Показатель | Как считается (`service/StatisticsService.java`) |
|---|---|
| ST-01…ST-03 | `summarize()` :56 — `passengers.count()`, `schedule.size()`, число рейсов в статусах SCHEDULED/DELAYED, `all.size()` |
| ST-04, ST-05 | `countByEnum()` :91 — `Collectors.groupingBy(…, counting())` в `EnumMap`, нулевые значения тоже; доля класса — `StatisticsResponse.fareClassSharePercent()` |
| ST-06 | `totalRevenue()` :100 — `filter(isPaid).map(getPrice).reduce(BigDecimal::add)` |
| ST-07 | `averagePaidPrice()` :109 — сумма / количество, `HALF_UP` |
| ST-08 | `averageLoad()` :121 — активные брони / сумма `total_seats`, % (`StatisticsResponse.percent()` dto/AnalyticsDtos.java:38) |
| ST-09 | `topFlights()` :78 — `groupingBy(Booking::getFlight)`, по убыванию, при равенстве — по времени вылета, `limit(5)` |
| ST-10 | `topRoutes()` :128 — группировка по маршруту, при равенстве — по самому раннему вылету, `limit(3)` |
| ST-11 | `summarize()` :58 — доля CANCELLED + EXPIRED |

Результат — record `StatisticsResponse` (dto/AnalyticsDtos.java:18, только значения); форматирование
(точки-выноски, суммы, проценты, «4 брони») делает `StatisticsPane` (ui/StatisticsPane.java: `printDistributions()` :60,
`printTopFlights()` :77, `printTopRoutes()` :89). Контрольные SQL-запросы — `console-app/sql/statistics_check.sql`.

**Экспорт:** главное меню «7» (ui/MainView.java:64) → `ExportPane.export()` (ui/ExportPane.java:49) →
BR-09 → для каждого формата `ExportService.createExporter(format)` (service/ExportService.java:46) возвращает
**ссылку типа `Exporter`** (ui/ExportPane.java:54) → `ExportService.export(exporter)` (service/ExportService.java:55) →
`transactions.read(bookings::findAll)` (:56) → `exporter.export(bookings, dir)` → `ExportResult(format, file, rows)`.
Для Excel справочники и статистика читаются при создании экспортёра (:48).

```
Exporter (interface)                     export/Exporter.java:16, export() :25
 └─ AbstractFileExporter (abstract)      export/AbstractFileExporter.java:26
     │  export() :48 — шаблонный метод: resolveTarget() :95 (каталог, имя bookings_yyyy-MM-dd_HH-mm, E-601)
     │                 → write() :60 → ошибки записи → ExportException (E-601)
     │  safeCell() :84 — защита от формул (=, +, -, @ в начале значения)
     ├─ ExcelExporter   export/ExcelExporter.java:30, write() :51 — Apache POI: 4 листа,
     │                  ExcelStyles.finishTable() export/ExcelStyles.java:68 — freeze pane, автофильтр, autoSizeColumn
     └─ CsvExporter     export/CsvExporter.java:27, write() :40 — UTF-8 + BOM, «;», CRLF, кавычки по RFC 4180 (Commons CSV)
```

`Workbook` и `OutputStream` (export/ExcelExporter.java:52, :59), `Writer` и `CSVPrinter` (export/CsvExporter.java:41, :44)
закрываются try-with-resources. Суммы и даты пишутся в Excel числами и датами (форматы `#,##0.00`,
`dd.mm.yyyy hh:mm` — export/ExcelStyles.java:53, :55). Номера документов в файлах маскированы.

---

## Ответы на обязательные вопросы (п. 12.2 ТЗ)

**Назначение основных классов.** `domain` — данные и поведение предметной области; `dto` — контракты
между экранами и сервисами; `repository` — интерфейсы, `repository/impl` — SQL и JDBC; `service` — бизнес-правила,
транзакции и преобразование в DTO; `filter` — критерии отбора и сортировки; `ui` — меню, ввод, вывод;
`error` — иерархия ошибок и их обработка; `export` — выгрузка; `jdbc` — соединение, транзакции, миграции;
`config` — параметры; `util` — даты, деньги, таблицы, русские подписи.
`AviaBookingLauncher.main()` (AviaBookingLauncher.java:22) только запускает `AviaBookingConsoleApplication` —
точку сборки: `run()` (AviaBookingConsoleApplication.java:72) читает конфигурацию, подключается к БД,
применяет миграции, создаёт репозитории → валидаторы и мапперы → сервисы (`createServices()` :175) →
экраны (`createMainView()` :157) и показывает главное меню. DI-контейнера нет — зависимости передаются в конструкторы.

**Инкапсуляция.** Все поля `Passenger`, `Flight`, `Booking` — `private`. У `Booking` нет сеттера
статуса: только `changeStatus()` с проверкой матрицы; `bookingRef`, `passenger`, `flight` — `final`
(domain/Booking.java:18). Конструктор не создаёт бронь с неположительной ценой (`requirePositive()` :110).
Метки времени скрыты в `AuditedEntity` (`onCreate()` domain/AuditedEntity.java:17). DTO — неизменяемые record.
`AppProperties.Db.toString()` скрывает пароль (config/AppProperties.java:53).

**Конструкторы.** У `Booking` — полный (из БД, domain/Booking.java:28) и для новой брони (статус CREATED, :42);
у перечислений — конструкторы с параметрами (`FareClass(title, coefficient, baggageIncluded)`,
`DocumentType(title, regex, formatHint)`); у record — компактные конструкторы с проверкой
(`BookingFilter` filter/BookingFilter.java:48, `AppProperties.Db` config/AppProperties.java:43).
Все зависимости сервисов и экранов передаются через публичный конструктор.

**Интерфейсы.** `CrudRepository<T, ID>` (repository/CrudRepository.java:15) и его наследники
`PassengerRepository`, `FlightRepository`, `BookingRepository` (repository/BookingRepository.java:16);
`TableViewRepository`; `Exporter`; `Transactions` (jdbc/Transactions.java:11). Сервисы зависят от интерфейсов,
поэтому в тестах подставляются репозитории в памяти (`src/test/java/ru/mirea/avia/support`) и
`Transactions.direct()` (jdbc/Transactions.java:20).

**enum.** `BookingStatus` — матрица переходов и признаки (`isFinal`, `occupiesSeat`, `isInProgress`, `isPaid`);
`FareClass` — коэффициенты; `FlightStatus` — доступность для продажи и переходы рейса;
`DocumentType` — регулярное выражение номера и маскирование; `BookingSort` — компараторы;
`ErrorCode` — все коды ошибок; `ExportDtos.ExportFormat`, `TableDtos.DatabaseTable`,
`BookingFilter.Operator`, `PassengerValidator.NameField`.

**Полиморфизм.** `ExportPane` работает со ссылкой `Exporter`, не зная формата (ui/ExportPane.java:54);
`ExcelExporter` и `CsvExporter` переопределяют `write()`, который вызывает шаблонный метод
`AbstractFileExporter.export()`. Три JDBC-реализации `CrudRepository` (и три — в памяти для тестов).
Две реализации `Transactions`: `DatabaseManager` и `Transactions.direct()`. `ConsoleErrorHandler.handle()`
выбирает сообщение `switch` по типу исключения (error/ConsoleErrorHandler.java:29). `Menu` получает действия
пунктов как `Runnable` (ссылки на методы панелей). Доменные классы переопределяют `toString()`, `equals()`, `hashCode()`.

**Коллекции и Stream API.** `List<BookingResponse>` везде; `Map<BookingStatus, Long>` (`EnumMap`) в статистике;
`Set<Long>` ID рейсов в SR-05/SR-06; `Map<Long, Integer>` занятых мест (`countActiveGroupByFlightId()`);
`List<Predicate<Booking>>` в `BookingSpecifications`; операции `filter`, `map`, `sorted`, `groupingBy`, `counting`,
`toMap`, `reduce`, `anyMatch`, `min`, `limit`, `toList()`.

**Обработка исключений.**
- Иерархия (error/AppException.java:9): `RuntimeException` ← `AppException` (хранит `ErrorCode`) ←
  `ValidationException` (E-1xx), `EntityNotFoundException` (E-2xx), `BusinessRuleException` (E-3xx) ←
  `SeatUnavailableException`, `InvalidStatusTransitionException`, `DuplicateBookingException`;
  `DataAccessException` (E-5xx); `ExportException` (E-601). Любое другое исключение отображается как E-500.
- Где выбрасываются: валидаторы, сервисы, доменные методы (`Booking.changeStatus()`), разбор умного фильтра.
  `SQLException` превращается в `DataAccessException` в `SqlErrors.translate()` (jdbc/SqlErrors.java:17)
  по SQLState: `08…`/`57P…` → E-501, `23…` → E-503 (:22), прочее → E-502. Вызывают его
  `AbstractJdbcRepository`, `DatabaseManager` и `MigrationRunner`.
- Где перехватываются: `InputPrompt.read()` (ui/InputPrompt.java:44) — ошибка ввода или правила, поле
  запрашивается заново (`printRetry()` :165), а `DataAccessException` пробрасывается дальше (:50);
  `CommandRunner.execute()` (ui/CommandRunner.java:29) — ошибка команды, сообщение и возврат в меню;
  `AviaBookingConsoleApplication.connect()` (AviaBookingConsoleApplication.java:131) — E-501 при старте с меню «1. Повторить попытку / 0. Выход»;
  `AviaBookingConsoleApplication.run()` (AviaBookingConsoleApplication.java:72) — конец ввода (`InputClosedException`, :82) и закрытие соединения в `finally`.
- Stack trace и SQLState пишутся в `avia-booking.log`, оператору — только текст на русском.
  `DatabaseManager` отключает подробности ошибок сервера (`setLogServerErrorDetail(false)`, jdbc/DatabaseManager.java:34),
  чтобы номера документов и e-mail не попадали в тексты ошибок (NFR-12).

### Обработка ошибок: CommandRunner, ConsoleErrorHandler, ErrorReport и correlation ID

```
Menu.execute()                                   ui/Menu.java:81
 └─ CommandRunner.execute(command, action)       ui/CommandRunner.java:29
      ├─ CorrelationId.open()                    :29 — свой ID на каждую команду («bookings.create», …)
      ├─ action.run()
      ├─ InputCancelledException → «Операция отменена», без паузы   :36
      ├─ InputClosedException → пробрасывается (конец ввода)
      └─ RuntimeException                         :41
           └─ ConsoleErrorHandler.handle(ex, command)   error/ConsoleErrorHandler.java:27
                ├─ E-501 → «Нет связи с базой данных. Проверьте, запущен ли сервер PostgreSQL»   :30
                ├─ E-503 → «Операция нарушает целостность данных и была отклонена» (имя ограничения скрыто)   :35
                ├─ прочие DataAccessException → E-502 «Ошибка обращения к данным, операция отменена»
                ├─ AppException → код и сообщение исключения
                └─ иное → E-500 «Внутренняя ошибка…», log.error со stack trace   :45
                ⇒ ErrorReport(timestamp, code, message, command, correlationId)   dto/ErrorReport.java:11
           └─ Ui.error(io, report) → «  Операция не выполнена [E-xxx]: текст»   ui/Ui.java:38
                для E-5xx добавляется « (correlation ID: первые 8 символов)»   :41
```

Зачем: `ErrorReport` — безопасный DTO без stack trace, SQL и имён классов; экран печатает только его.
Технические подробности записывает `ConsoleErrorHandler` в журнал вместе с именем команды, а correlation ID
связывает строку в консоли со строкой в журнале (как `X-Correlation-Id` в веб-приложении эталона).

**Почему `CorrelationId`, а не MDC.** В эталоне ID кладётся в `MDC` (`CorrelationIdFilter`). Бэкенд журнала
консольного приложения — `slf4j-simple`, а он MDC не поддерживает (`MDC.put()` ничего не сохраняет). Поэтому ID
хранится в собственном `ThreadLocal` — `error/CorrelationId.java`: `open()` создаёт ID команды и возвращает
внешний, `restore()` возвращает внешний после вложенной команды (раздел меню → его операции), `current()` читает
`ConsoleErrorHandler`. Ошибка подключения при старте (`AviaBookingConsoleApplication.connect()`) тоже получает ID.

### Зачем DTO между экранами и сервисами

- Экран не может изменить доменный объект в обход правил: у него нет ссылки на `Booking`, значит, нельзя вызвать
  `modify()` без проверки BR-03.
- Ответ содержит готовые производные поля: `shortName`, `maskedDocument`, `age` (`PassengerMapper`
  service/PassengerMapper.java:18), `route`, `rowCount`, `allowedStatuses` (`FlightMapper` service/FlightMapper.java:13,
  `BookingMapper` service/BookingMapper.java:20). Экран их не вычисляет.
- DTO собирается внутри транзакции; после commit экран работает с неизменяемыми данными, соединение уже закрыто.
- Входные DTO нормализуются и проверяются целиком перед записью: `PassengerValidator.validate()`
  (service/PassengerValidator.java:106), `FlightValidator.validate()` (service/FlightValidator.java:104).
  `BookingRequest` вообще не содержит цены — её считает система (BR-06).
- Это тот же приём, что контроллер и сервис со `@Service` в эталонном проекте: публичный контракт сервиса не зависит
  от устройства сущностей и таблиц. Исключение — `Exporter`, внутренний слой выгрузки, который получает `List<Booking>`.

### Транзакции и соединение

- `Transactions` (jdbc/Transactions.java:11): `read(Supplier)` (:14) — аналог `@Transactional(readOnly = true)`,
  `write(Supplier)` (:17) — изменение в одной транзакции. Сервисы оборачивают работу в
  `transactions.read(() -> …)` / `transactions.write(() -> …)`.
- `DatabaseManager implements Transactions` (jdbc/DatabaseManager.java:20), метод `execute()` (:75):
  1. если в этом потоке транзакция уже открыта (`current.get() != null`, :76), работа выполняется в ней —
     так `ExportService.createExporter()` вызывает `statistics.calculate()` внутри своей транзакции;
  2. иначе `try (Connection connection = openConnection())` (:77) — соединение из `PGSimpleDataSource`,
     `setAutoCommit(false)` (:78), `setReadOnly(readOnly)` (:79), соединение кладётся в `ThreadLocal` (:24);
  3. `work.get()` → `commit()` (:83); при `RuntimeException` или `Error` → `rollback()` (:86, :105) и повторный выброс;
  4. `finally current.remove()`, а try-with-resources закрывает соединение.
- Репозитории берут соединение через `database.connection()` (:59) и не закрывают его — владелец соединения
  `DatabaseManager`; `PreparedStatement` и `ResultSet` закрываются try-with-resources
  (`AbstractJdbcRepository.query()` repository/impl/AbstractJdbcRepository.java:36, `executeUpdate()` :70,
  `insertAndGetId()` :85).
- Между командами меню открытых соединений нет; приложение однопользовательское, пул не нужен (CON-05).
  При старте — `checkConnection()` (jdbc/DatabaseManager.java:38, FR-22, NFR-07), при выходе — `close()` (:71, FR-23).
- Правило: запись нельзя вызывать внутри чтения (соединение только для чтения). Поэтому BR-09
  (`expireOutdated()`, своя write-транзакция) выполняется **до** открытия транзакции операции
  (service/BookingService.java:68, :69).

### Flyway

- `MigrationRunner.migrate()` (jdbc/MigrationRunner.java:31) вызывается при старте после проверки соединения,
  до создания сервисов (`runner.run("startup.migrate", …)`, AviaBookingConsoleApplication.java:98).
- Настройки: `dataSource()` из `DatabaseManager`, схема `avia` (создаётся при необходимости),
  каталоги `classpath:db/migration` и — при `DEMO_DATA=true` — `classpath:db/demo` (jdbc/MigrationRunner.java:33),
  `baselineOnMigrate(true)` и `baselineVersion("2")` (jdbc/MigrationRunner.java:41).
- `V1__initial_schema.sql` создаёт таблицы и ограничения, `V2__demo_data.sql` — демо-набор. Данные лежат отдельно,
  чтобы можно было поднять пустую схему (`DEMO_DATA=false`).
- Применённые версии и контрольные суммы хранятся в `avia.flyway_schema_history`; изменённый после применения файл
  Flyway не примет, поэтому схема меняется только новой миграцией `V3`, `V4`, ….
- Ручная загрузка `sql/schema.sql` + `sql/data.sql` даёт непустую схему без истории — Flyway фиксирует её как
  версию 2 и ничего не применяет. `scripts/verify.py` проверяет, что psql-скрипты совпадают с V1 и V2.
- `reset-demo.sh` удаляет схему вместе с историей, поэтому следующий запуск заново применяет V1 и V2.
- Flyway работает поверх JDBC и не является ORM; SQL самого приложения по-прежнему только в `repository/impl`.
  Ошибка миграции переводится в E-501/E-502/E-503 (`translate()` jdbc/MigrationRunner.java:51), и приложение не запускается.

### Умный фильтр: SmartFilterParser → BookingFilter → BookingSpecifications

- Строка разбивается на слова регулярным выражением `TOKEN` (filter/SmartFilterParser.java:26), фразу можно взять
  в кавычки. Слово вида `цена>=15000` разбирает `PRICE` (:27), слово `ключ:значение` — `Criteria.apply()` (:125):
  каждый ключ по `switch` записывается в типизированное поле (статус → `BookingStatus`, класс → `FareClass`,
  даты → `LocalDate`). Неизвестное значение — `ValidationException` E-103/E-102, и `InputPrompt` просит ввести строку заново.
- `Criteria.toFilter()` (:141) создаёт неизменяемый `BookingFilter`; его компактный конструктор проверяет, что
  дата «с» не позже «по» и минимум не больше максимума.
- `BookingSpecifications.from()` (filter/BookingSpecifications.java:26) превращает каждый заданный критерий в
  `Predicate<Booking>` и объединяет их через `and` (:45); незаданный критерий ничего не ограничивает.
- Строка фильтра **никогда не попадает в SQL**: выборка читается фиксированным запросом `findAll()`, а отбор идёт
  в Java (FR-13 требует Stream API). Поэтому SQL-инъекция через фильтр невозможна.

**Работа JDBC.**
- `DatabaseManager` (jdbc/DatabaseManager.java) — единственное место, где открывается соединение
  (`PGSimpleDataSource` с параметрами из `AppProperties.Db`, пароль — из `.env`). Подробно — в разделе
  «Транзакции и соединение».
- `AbstractJdbcRepository` (repository/impl/AbstractJdbcRepository.java): `query()` (:36), `queryForList()` (:48), `queryForObject()` (:59), `queryForLong()` (:65),
  `executeUpdate()` (:70), `insertAndGetId()` (:85) — `PreparedStatement`, параметры через `bind()` (:99)
  типизированными `setString`, `setLong`, `setInt`, `setBigDecimal`, `setBoolean`, `setTimestamp`, `setDate`;
  перечисления — по `name()`, `null` — `setNull`. `ResultSet` закрывается try-with-resources.

**Statement vs PreparedStatement (п. 9.6.1).** В `Statement` запрос склеивается из строк — уязвим
для SQL-инъекции, план строится заново, даты и числа форматируются вручную. `PreparedStatement` —
текст фиксирован, значения передаются параметрами `?` и никогда не становятся частью SQL; запрос
прекомпилируется; есть типизированные `setXxx()` и пакетная обработка. В проекте используется только
`PreparedStatement` (это проверяет `scripts/verify.py`). Имя таблицы параметром передать нельзя, поэтому в
`TableViewRepositoryImpl` текст запроса выбирается из фиксированного набора по перечислению `DatabaseTable`
(`selectSql()` repository/impl/TableViewRepositoryImpl.java:66), а `LIMIT ? OFFSET ?` передаются параметрами.

**Связи между таблицами** (`db/migration/V1__initial_schema.sql` = `console-app/sql/schema.sql`,
[er-diagram.png](er-diagram.png)). `passengers 1 — N bookings` (`fk_bookings_passenger`),
`flights 1 — N bookings` (`fk_bookings_flight`), оба `ON DELETE RESTRICT` (BR-10). `bookings` разрешает связь
«многие ко многим» и сама является сущностью. Частичные уникальные индексы действуют только для броней не в
CANCELLED/EXPIRED — поэтому после отмены место и «слот» пассажира освобождаются.

**Почему SQL только в repository.** Слои зависят в одну сторону (`ui → service → repository`);
сервисы не знают, какая СУБД, и тестируются без неё; смена PostgreSQL на MySQL затрагивает только
`repository/impl`, конфигурацию и миграции (NFR-21, NFR-23). `scripts/verify.py` проверяет, что SQL-литералы
есть только в `repository/impl`, а консольный ввод-вывод — только в `ui`.

**Основные бизнес-правила.**

| Правило | Метод | Код |
|---|---|---|
| BR-01 рейс открыт для продажи (статус, ≥ 60 мин до вылета) | `BookingValidator.checkOpenForSale` (service/BookingValidator.java:77), `Flight.isOpenForSale` (domain/Flight.java:61) | E-300 |
| BR-02 есть свободные места | `BookingValidator.checkCapacity` (service/BookingValidator.java:102) | E-301 |
| BR-03 место свободно | `BookingValidator.checkSeatFree` (service/BookingValidator.java:109) + индекс | E-301 |
| BR-04 нет второй активной брони на рейс | `BookingValidator.checkNoDuplicate` (service/BookingValidator.java:121) + индекс | E-303 |
| BR-05 пассажир и рейс существуют | `BookingService.requirePassenger` / `requireFlight` (service/BookingService.java:237, service/BookingService.java:241) + FK | E-202 |
| BR-06 цена считается системой | `FareCalculator.calculate` (service/FareCalculator.java:17) + CHECK | E-103 |
| BR-07 матрица переходов | `BookingStatus.canTransitionTo` (domain/BookingStatus.java:55), `BookingValidator.checkStatusChange` (service/BookingValidator.java:146) | E-302 |
| BR-08 отмена из CREATED/PAID не позже чем за 2 ч | `BookingValidator.checkCanCancel` (service/BookingValidator.java:173) | E-302, E-304 |
| BR-09 аннулирование через 30 мин | `BookingService.expireOutdated` (service/BookingService.java:195) | — |
| BR-10 удаление только CANCELLED/EXPIRED; нельзя удалить пассажира/рейс с бронями | `BookingValidator.checkCanDelete` (service/BookingValidator.java:196), `PassengerService.checkCanDelete` (service/PassengerService.java:91), `FlightService.checkCanDelete` (service/FlightService.java:125) + FK | E-305 |

**Создание, изменение, удаление записей** — разделы 1 и 2 выше. **Поиск, фильтрация, сортировка** —
раздел 3. **Статистика и экспорт** — раздел 4.
