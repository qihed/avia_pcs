# Быстрый старт АВИА-БРОНЬ

Консольное приложение живёт в каталоге `console-app/`. PostgreSQL 16 запускается в Docker, а приложение — на компьютере через Maven Wrapper.
Схему `avia` и демонстрационные данные создаёт Flyway при первом запуске приложения, поэтому вручную выполнять SQL не нужно.

## Что нужно установить

| Инструмент | Зачем | Проверка |
|---|---|---|
| **JDK 21** (Temurin, OpenJDK и т. п.) | сборка и запуск приложения | `java -version` показывает `21` |
| Docker Desktop (Windows, macOS) или Docker Engine с плагином Compose v2 (Linux) | PostgreSQL 16 в контейнере | `docker compose version` |
| Python 3 (необязательно) | `scripts/verify.py` и `scripts/package_release.py` | `python3 --version` |

Maven устанавливать не нужно: его скачает `mvnw` / `mvnw.cmd`. Порт `5432` должен быть свободен (как выбрать другой, см. раздел «Если что-то пошло не так»).

## macOS / Linux

```bash
cd console-app
./init-env.sh          # 1. создаёт .env со случайным паролем PostgreSQL
./start-compose.sh     # 2. поднимает PostgreSQL 16 и ждёт healthcheck
./run.sh               # 3. компилирует и запускает консольное приложение
```

## Windows (PowerShell)

```powershell
cd console-app
.\init-env.ps1         # 1. создаёт .env со случайным паролем PostgreSQL
.\start-compose.ps1    # 2. поднимает PostgreSQL 16 и ждёт healthcheck
.\run.ps1              # 3. переключает консоль на UTF-8 и запускает приложение
```

Если PowerShell запрещает запуск скриптов, разрешите их только для текущего окна:
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`.

`start-compose` сам вызовет `init-env`, если файла `.env` ещё нет. Пароль нигде не печатается: он хранится только в `console-app/.env`.
Этот файл не коммитится и никому не пересылается.

## Первый запуск

1. Приложение подключается к `jdbc:postgresql://127.0.0.1:5432/avia_booking?currentSchema=avia`.
2. Flyway создаёт схему `avia` (миграция `V1`) и загружает демонстрационный набор (`V2`): 6 пассажиров, 8 рейсов и 14 бронирований во всех статусах.
   Если в `.env` задано `DEMO_DATA=false`, создаётся только пустая схема.
3. Стартовая памятка показывает адрес базы, пользователя, каталог выгрузки и файл журнала `avia-booking.log`. Пароли в ней не выводятся.
4. Открывается главное меню. Выход — пункт `0`.

Выгрузки XLSX/CSV сохраняются в `console-app/exports/` (переменная `EXPORT_DIR`).

## Сброс демонстрационных данных

Три брони в статусе «Создано, ожидает оплаты» через 30 минут становятся просроченными (правило BR-09). Перед показом верните исходный набор.
Сначала закройте приложение, затем выполните:

```bash
cd console-app
./reset-demo.sh        # Windows: .\reset-demo.ps1
```

Скрипт попросит ввести `RESET` и удалит схему `avia` командой `DROP SCHEMA IF EXISTS avia CASCADE` внутри контейнера `db`.
После этого снова запустите приложение (`./run.sh` или `.\run.ps1`): Flyway заново создаст схему и загрузит демонстрационные данные.

## Резервная копия и восстановление

Команды выполняются из корня репозитория, пока контейнер `db` запущен. В Windows используйте Git Bash или WSL.

```bash
scripts/backup-postgres.sh
# Backup: backups/avia_booking_20260917T120000Z.dump (+ .sha256 рядом)

scripts/restore-postgres.sh backups/avia_booking_20260917T120000Z.dump
# проверяет контрольную сумму и просит ввести RESTORE
```

Каталог `backups/` в git не попадает.

## Остановка

```bash
cd console-app
./stop-compose.sh      # Windows: .\stop-compose.ps1
```

Данные сохраняются в volume `avia_booking_postgres`. Полностью удалить базу вместе с данными можно командой `docker compose down -v` в каталоге `console-app`.

## Запуск без JDK (только Docker)

```bash
cd console-app
docker compose --profile app run --rm app
```

Контейнер `maven:3.9.11-eclipse-temurin-21` компилирует исходники и запускает меню в текущем терминале. Выгрузки появляются в `console-app/exports/`.

> На Linux (Docker Engine без Docker Desktop) контейнер работает от root, поэтому файлы в
> `console-app/exports/` принадлежат root: удалять их нужно через `sudo rm`. В macOS и Windows
> Docker Desktop подменяет владельца на пользователя хоста. Если это мешает, запускайте приложение
> с локальным JDK (`./run.sh`) — тогда выгрузки создаются от вашего пользователя.

## Проверка и сборка

```bash
scripts/build-all.sh                           # Windows: scripts\build-all.ps1
python3 scripts/verify.py                      # статические проверки
python3 scripts/verify.py --build              # плюс mvnw clean verify и docker compose config
python3 scripts/package_release.py             # ZIP рядом с каталогом проекта и .sha256
```

`verify.py` записывает отчёт `VERIFICATION.md` и `SOURCE_MANIFEST.sha256` в корень репозитория. Логи сборки лежат в `build-reports/`.

Сквозной прогон без ручного ввода (как в CI):

```bash
cd console-app
./mvnw -B -ntp -DskipTests clean package
java -jar target/avia-booking.jar < ../examples/demo-session.txt
```

## Параметры

Приложение берёт значения по порядку: `-DVAR`, переменная окружения, `console-app/.env`, значение по умолчанию.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `POSTGRES_DB` | `avia_booking` | имя базы в контейнере |
| `POSTGRES_USER` | `avia_app` | владелец базы |
| `POSTGRES_PASSWORD` | — (создаёт `init-env`) | пароль PostgreSQL |
| `POSTGRES_PORT` | `5432` | порт на `127.0.0.1` |
| `DB_URL` | `jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB}?currentSchema=avia` | адрес JDBC |
| `DB_USER`, `DB_PASSWORD` | значения `POSTGRES_USER`, `POSTGRES_PASSWORD` | учётные данные приложения |
| `DEMO_DATA` | `true` | загружать ли демонстрационный набор |
| `EXPORT_DIR` | `exports` | каталог выгрузок |
| `APP_TIME_ZONE` | `Europe/Moscow` | часовой пояс приложения |

## Если что-то пошло не так

- **Порт 5432 занят.** Если базы ещё нет, пересоздайте `.env`: `./init-env.sh --port 5440 --force` (Windows: `.\init-env.ps1 -PostgresPort 5440 -Force`).
  Если volume с базой уже создан, поменяйте в `.env` только `POSTGRES_PORT`. Новый `.env` содержит новый пароль, а база в volume сохранила старый.
- **Ошибка E-501 «Нет соединения с базой данных».** Проверьте, что контейнер запущен (`docker compose ps` в `console-app`), и повторите попытку из меню приложения.
- **Не тот compose-файл.** Выполните `./verify-compose.sh` (Windows: `.\verify-compose.ps1`). Первая строка файла должна быть `# AVIA_BOOKING_COMPOSE_V1`.
- **Кракозябры в Windows.** Запускайте приложение через `.\run.ps1`: скрипт переключает консоль на UTF-8.
- **`java -version` показывает не 21.** Укажите `JAVA_HOME` на JDK 21: `mvnw` использует именно его.
