# Проверка проекта

Результат: **PASS: 37; FAIL: 0; SKIP: 0.**

`SKIP` означает, что в текущей среде нет нужного инструмента или SDK.

| Проверка | Статус | Детали | Время |
|---|---:|---|---:|
| Файл/каталог `console-app/pom.xml` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/mvnw` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/mvnw.cmd` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/.mvn/wrapper/maven-wrapper.properties` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/src/main/java` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/src/main/resources/application.properties` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/src/main/resources/db/migration/V1__initial_schema.sql` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/src/main/resources/db/demo/V2__demo_data.sql` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/sql/schema.sql` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/sql/data.sql` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/docker-compose.yml` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/.env.example` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/init-env.ps1` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/init-env.sh` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/start-compose.ps1` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/start-compose.sh` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/run.sh` | ✅ PASS | найден | 0.0s |
| Файл/каталог `console-app/run.ps1` | ✅ PASS | найден | 0.0s |
| Файл/каталог `README.md` | ✅ PASS | найден | 0.0s |
| Файл/каталог `docs/QUICKSTART.md` | ✅ PASS | найден | 0.0s |
| Файл/каталог `docs/er-diagram.png` | ✅ PASS | найден | 0.0s |
| Java-исходники | ✅ PASS | 127 файлов (main: 95, test: 32) | 0.0s |
| JavaDoc | ✅ PASS | обнаружено 430 блоков JavaDoc на 95 файлов main | 0.0s |
| Нет Spring/JPA/JavaFX/Hibernate/Lombok | ✅ PASS | запрещённых импортов нет | 0.0s |
| SQL только в repository/impl | ✅ PASS | SQL-литералы встречаются только в repository/impl | 0.0s |
| Консольный ввод-вывод только в ui | ✅ PASS | System.out/System.in/Scanner/PrintStream используются только в ui | 0.0s |
| Нет конкатенации SQL со строкой | ✅ PASS | SQL передаётся в JDBC только готовыми константами | 0.0s |
| Только PreparedStatement | ✅ PASS | createStatement( не используется | 0.0s |
| Нет захардкоженных паролей | ✅ PASS | пароли берутся только из .env и окружения | 0.0s |
| Нет TODO/FIXME/заглушек | ✅ PASS | TODO, FIXME и UnsupportedOperationException не найдены | 0.0s |
| Класс ≤ 300 строк | ✅ PASS | проверено 95 файлов | 0.0s |
| Метод ≤ 40 строк | ✅ PASS | проверено 95 файлов | 0.0s |
| Коды ошибок ErrorCode | ✅ PASS | все 17 обязательных кодов на месте | 0.0s |
| Схема: sql/schema.sql = V1 | ✅ PASS | 14 операторов совпадают | 0.0s |
| Данные: sql/data.sql = V2 | ✅ PASS | 3 операторов совпадают | 0.0s |
| Docker Compose: маркер и порт | ✅ PASS | маркер, образ PostgreSQL 16 и локальный порт на месте | 0.0s |
| Скан известных секретов | ✅ PASS | ключи/токены не найдены | 0.0s |

## Контрольные суммы

`SOURCE_MANIFEST.sha256` содержит 171 хешей исходников и конфигурации.
