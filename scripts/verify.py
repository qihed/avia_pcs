#!/usr/bin/env python3
"""Структурная проверка проекта АВИА-БРОНЬ (консольное приложение на JDBC и PostgreSQL)."""
from __future__ import annotations

import argparse
import bisect
import hashlib
import os
import re
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "VERIFICATION.md"
MANIFEST = ROOT / "SOURCE_MANIFEST.sha256"
MODULE = ROOT / "console-app"
MAIN_JAVA = MODULE / "src/main/java"
BASE_PACKAGE = MAIN_JAVA / "ru/mirea/avia"
RESOURCES = MODULE / "src/main/resources"

MIN_JAVA_FILES = 60
MAX_CLASS_LINES = 300
MAX_METHOD_LINES = 40
ERROR_CODES = (
    "E-101", "E-102", "E-103", "E-104", "E-105", "E-201", "E-202",
    "E-300", "E-301", "E-302", "E-303", "E-304", "E-305",
    "E-501", "E-502", "E-503", "E-601",
)

# Сборка, локальные данные и секреты не относятся к исходникам: их нет ни в манифесте, ни в скане.
EXCLUDED_DIRS = {"target", ".idea", ".vscode", ".git", "__pycache__", "build-reports", "backups", "exports"}
EXCLUDED_NAMES = {"SOURCE_MANIFEST.sha256", "VERIFICATION.md", ".DS_Store", "Thumbs.db"}
EXCLUDED_SUFFIXES = {".log", ".zip"}

FORBIDDEN_IMPORT = re.compile(
    r"^\s*import\s+(?:static\s+)?(org\.springframework|jakarta\.persistence|javax\.persistence"
    r"|javafx|org\.hibernate|lombok)\b", re.M)
SQL_KEYWORDS = re.compile(r"SELECT |INSERT INTO|UPDATE |DELETE FROM")
CONSOLE_IO = re.compile(r"\bSystem\s*\.\s*(?:out|err|in)\b|\bScanner\b|\bPrintStream\b")
SQL_CALL = re.compile(r"\b(?:prepareStatement|prepareCall|executeQuery|executeUpdate|executeLargeUpdate|addBatch)\s*\(")
CREATE_STATEMENT = re.compile(r"\bcreateStatement\s*\(")
JAVA_PASSWORD = re.compile(r"(?i)password\s*=\s*\"[^\"]+\"")
PROPERTY_PASSWORD = re.compile(r"(?im)^[ \t]*([\w.-]*password[\w.-]*)[ \t]*[=:][ \t]*(?!\$\{)\S")
COMPOSE_PASSWORD = re.compile(r"(?im)^[ \t]*(\w*PASSWORD\w*)[ \t]*:[ \t]*(?!\$\{)\S")
PLACEHOLDER = re.compile(r"(?m)^POSTGRES_PASSWORD=REPLACE_ME\s*$")
STUBS = re.compile(r"\bTODO\b|\bFIXME\b|UnsupportedOperationException")
# Эвристика начала метода: строка с отступом 4 пробела, модификатор или тип, имя и открывающая скобка.
METHOD_WITH_MODIFIER = re.compile(
    r"^ {4}(?:public|protected|private|static|final|abstract|synchronized|default)\b[^=;{}()]*?\b\w+\s*\(")
METHOD_PACKAGE_PRIVATE = re.compile(r"^ {4}(?:<[^>]*>\s+)?[\w.$]+(?:<[^=;{}()]*>)?(?:\[\])*\s+\w+\s*\(")
TYPE_DECLARATION = re.compile(r"\b(?:class|interface|enum|record)\s+\w+|@interface\b")
DDL = re.compile(r"(?i)^(?:CREATE|ALTER|DROP|COMMENT)\b")
SCHEMA_SETUP = re.compile(r"(?i)^(?:DROP\s+SCHEMA|CREATE\s+SCHEMA|SET\s+search_path)\b")
INSERT = re.compile(r"(?i)^INSERT\b")
SECRET_PATTERNS = {
    "private key": r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
    "AWS key": r"AKIA[0-9A-Z]{16}",
    "GitHub token": r"gh[pousr]_[A-Za-z0-9_]{30,}",
    "JWT literal": r"eyJ[a-zA-Z0-9_-]{10,}\.eyJ[a-zA-Z0-9_-]{10,}\.",
}


@dataclass
class Check:
    name: str
    status: str
    details: str
    seconds: float = 0.0


@dataclass
class JavaSource:
    path: Path
    text: str
    code: str
    literals: list[str]

    @property
    def name(self) -> str:
        try:
            return self.path.relative_to(BASE_PACKAGE).as_posix()
        except ValueError:
            return self.path.relative_to(ROOT).as_posix()

    def in_package(self, *parts: str) -> bool:
        try:
            relative = self.path.relative_to(BASE_PACKAGE).parts
        except ValueError:
            return False
        return relative[:len(parts)] == parts


checks: list[Check] = []


def add(name: str, status: str, details: str, seconds: float = 0.0) -> None:
    checks.append(Check(name, status, details.replace("\n", " ").strip(), seconds))


def require(path: str) -> None:
    target = ROOT / path
    add(f"Файл/каталог `{path}`", "PASS" if target.exists() else "FAIL",
        "найден" if target.exists() else "отсутствует")


def verdict(name: str, problems: list[str], success: str) -> None:
    """Добавляет проверку: PASS, если нарушений нет, иначе FAIL с первыми нарушениями."""
    if not problems:
        add(name, "PASS", success)
        return
    shown = "; ".join(problems[:12])
    rest = f"; … ещё {len(problems) - 12}" if len(problems) > 12 else ""
    add(name, "FAIL", shown + rest)


def read_files(suffixes: tuple[str, ...], roots: Iterable[Path]) -> list[Path]:
    result: list[Path] = []
    for base in roots:
        if base.exists():
            result.extend(p for p in base.rglob("*") if p.is_file() and p.suffix.lower() in suffixes)
    return sorted(result)


def run(name: str, command: list[str], cwd: Path, timeout: int) -> None:
    is_path = os.path.sep in command[0] or "/" in command[0]
    executable = (cwd / command[0]).resolve() if is_path else shutil.which(command[0])
    if not executable or (isinstance(executable, Path) and not executable.exists()):
        add(name, "SKIP", f"инструмент `{command[0]}` отсутствует")
        return
    started = time.monotonic()
    try:
        # Вывод Maven содержит кириллицу, поэтому кодировка задаётся явно, а не по локали ОС.
        process = subprocess.run(command, cwd=cwd, encoding="utf-8", errors="replace", stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, timeout=timeout, check=False)
    except subprocess.TimeoutExpired:
        add(name, "FAIL", f"превышен таймаут {timeout} секунд", time.monotonic() - started)
        return
    except OSError as error:
        add(name, "FAIL", f"не удалось запустить `{command[0]}`: {error}", time.monotonic() - started)
        return
    elapsed = time.monotonic() - started
    log_dir = ROOT / "build-reports"
    log_dir.mkdir(exist_ok=True)
    log_name = re.sub(r"[^a-zA-Z0-9._-]+", "-", name.lower()).strip("-") + ".log"
    (log_dir / log_name).write_text(process.stdout, encoding="utf-8", errors="replace")
    tail = " | ".join(process.stdout.splitlines()[-5:])[-1200:]
    add(name, "PASS" if process.returncode == 0 else "FAIL",
        f"код {process.returncode}; лог `build-reports/{log_name}`; {tail}", elapsed)


def is_source_file(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if any(part in EXCLUDED_DIRS for part in relative.parts[:-1]):
        return False
    if path.name in EXCLUDED_NAMES or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    # .env и его локальные варианты хранят пароли; шаблон .env.example — обычный исходник.
    return not (path.name == ".env" or (path.name.startswith(".env.") and path.name != ".env.example"))


def project_files() -> list[Path]:
    result: list[Path] = []
    for folder, dirs, names in os.walk(ROOT):
        # Исключённые каталоги отсекаются до обхода: .git и target могут быть большими.
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
        result.extend(Path(folder) / name for name in names)
    return sorted(p for p in result if p.is_file() and is_source_file(p))


def scan_java(text: str) -> tuple[str, list[str]]:
    """Отделяет Java-код от комментариев и литералов.

    Возвращает код без комментариев, в котором строковые и символьные литералы заменены пустыми,
    и содержимое строковых литералов, включая text blocks. Переводы строк сохраняются,
    поэтому номера строк кода совпадают с исходным файлом.
    """
    code: list[str] = []
    literals: list[str] = []
    index, size = 0, len(text)
    while index < size:
        if text.startswith("//", index):
            end = text.find("\n", index)
            index = size if end < 0 else end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            end = size if end < 0 else end + 2
            code.append("\n" * text.count("\n", index, end))
            index = end
        elif text.startswith('"""', index):
            end = index + 3
            while True:
                end = text.find('"""', end)
                if end < 0 or text[end - 1] != "\\":
                    break
                end += 1
            end = size if end < 0 else end + 3
            literals.append(text[index + 3:end - 3])
            code.append('""' + "\n" * text.count("\n", index, end))
            index = end
        elif text[index] in "\"'":
            quote = text[index]
            end = index + 1
            while end < size and text[end] not in (quote, "\n"):
                end += 2 if text[end] == "\\" else 1
            if quote == '"':
                literals.append(text[index + 1:end])
            code.append(quote * 2)
            index = end + 1 if end < size and text[end] == quote else end
        else:
            code.append(text[index])
            index += 1
    return "".join(code), literals


def load_java(path: Path) -> JavaSource:
    text = path.read_text(encoding="utf-8", errors="replace")
    code, literals = scan_java(text)
    return JavaSource(path, text, code, literals)


def first_argument(code: str, start: int) -> str:
    depth = 0
    for index in range(start, len(code)):
        char = code[index]
        if char in "([{":
            depth += 1
        elif char in ")]}":
            if depth == 0:
                return code[start:index]
            depth -= 1
        elif char == "," and depth == 0:
            return code[start:index]
    return code[start:]


def method_end(code: str, start: int) -> int | None:
    """Возвращает смещение закрывающей скобки тела метода или None для объявления без тела."""
    parens = braces = 0
    in_body = False
    for index in range(start, len(code)):
        char = code[index]
        if char == "(":
            parens += 1
        elif char == ")":
            parens -= 1
        elif char == ";" and parens == 0 and not in_body:
            return None
        elif char == "{":
            braces += 1
            in_body = in_body or parens == 0
        elif char == "}":
            braces -= 1
            if in_body and braces == 0:
                return index
    return None


def long_methods(source: JavaSource) -> list[str]:
    lines = source.code.split("\n")
    starts = [0]
    for line in lines[:-1]:
        starts.append(starts[-1] + len(line) + 1)
    result: list[str] = []
    for number, line in enumerate(lines):
        if not (METHOD_WITH_MODIFIER.match(line) or METHOD_PACKAGE_PRIVATE.match(line)):
            continue
        if TYPE_DECLARATION.search(line):
            continue
        end = method_end(source.code, starts[number])
        if end is None:
            continue
        length = bisect.bisect_right(starts, end) - number
        if length > MAX_METHOD_LINES:
            result.append(f"{source.name}:{number + 1} ({length} строк)")
    return result


def sql_statements(path: Path) -> list[str]:
    """Возвращает операторы SQL-файла без комментариев, с нормализованными пробелами."""
    text = path.read_text(encoding="utf-8")
    statements: list[str] = []
    current: list[str] = []
    quoted = False
    index = 0
    while index < len(text):
        if not quoted and text.startswith("--", index):
            end = text.find("\n", index)
            index = len(text) if end < 0 else end
            continue
        if not quoted and text.startswith("/*", index):
            end = text.find("*/", index + 2)
            index = len(text) if end < 0 else end + 2
            continue
        char = text[index]
        if char == "'":
            quoted = not quoted
        if char == ";" and not quoted:
            statements.append(" ".join("".join(current).split()))
            current = []
        else:
            current.append(char)
        index += 1
    statements.append(" ".join("".join(current).split()))
    return [s for s in statements if s]


def compare_sql(name: str, left: Path, right: Path, keep: Callable[[str], bool]) -> None:
    missing = [p.relative_to(ROOT).as_posix() for p in (left, right) if not p.exists()]
    if missing:
        add(name, "FAIL", "файл отсутствует: " + ", ".join(missing))
        return
    first = [s for s in sql_statements(left) if keep(s)]
    second = [s for s in sql_statements(right) if keep(s)]
    if not first:
        add(name, "FAIL", f"в {left.relative_to(ROOT).as_posix()} не найдено ни одного оператора")
        return
    if first == second:
        add(name, "PASS", f"{len(first)} операторов совпадают")
        return
    for index, (a, b) in enumerate(zip(first, second)):
        if a != b:
            add(name, "FAIL", f"оператор №{index + 1} различается: `{a[:90]}` ≠ `{b[:90]}`")
            return
    add(name, "FAIL", f"разное число операторов: {len(first)} и {len(second)}")


for item in [
    "console-app/pom.xml", "console-app/mvnw", "console-app/mvnw.cmd",
    "console-app/.mvn/wrapper/maven-wrapper.properties", "console-app/src/main/java",
    "console-app/src/main/resources/application.properties",
    "console-app/src/main/resources/db/migration/V1__initial_schema.sql",
    "console-app/src/main/resources/db/demo/V2__demo_data.sql",
    "console-app/sql/schema.sql", "console-app/sql/data.sql",
    "console-app/docker-compose.yml", "console-app/.env.example",
    "console-app/init-env.ps1", "console-app/init-env.sh",
    "console-app/start-compose.ps1", "console-app/start-compose.sh",
    "console-app/run.sh", "console-app/run.ps1",
    "README.md", "docs/QUICKSTART.md", "docs/er-diagram.png",
]: require(item)

# Java-исходники и документация
java_files = read_files((".java",), [MODULE / "src"])
main_sources = [load_java(p) for p in read_files((".java",), [MAIN_JAVA])]
javadocs = sum(source.text.count("/**") for source in main_sources)
add("Java-исходники", "PASS" if len(java_files) >= MIN_JAVA_FILES else "FAIL",
    f"{len(java_files)} файлов (main: {len(main_sources)}, test: {len(java_files) - len(main_sources)})")
add("JavaDoc", "PASS" if main_sources and javadocs >= len(main_sources) else "FAIL",
    f"обнаружено {javadocs} блоков JavaDoc на {len(main_sources)} файлов main")

# Архитектурные ограничения ТЗ: консоль, plain JDBC, SQL только в repository/impl.
verdict("Нет Spring/JPA/JavaFX/Hibernate/Lombok",
        [f"{s.name}: {m.group(1)}" for s in main_sources for m in FORBIDDEN_IMPORT.finditer(s.text)],
        "запрещённых импортов нет")
verdict("SQL только в repository/impl",
        [s.name for s in main_sources
         if not s.in_package("repository", "impl") and any(SQL_KEYWORDS.search(v) for v in s.literals)],
        "SQL-литералы встречаются только в repository/impl")
verdict("Консольный ввод-вывод только в ui",
        [f"{s.name}: {m.group(0)}" for s in main_sources if not s.in_package("ui")
         for m in CONSOLE_IO.finditer(s.code)],
        "System.out/System.in/Scanner/PrintStream используются только в ui")
verdict("Нет конкатенации SQL со строкой",
        [f"{s.name}:{s.code.count(chr(10), 0, m.start()) + 1}" for s in main_sources
         for m in SQL_CALL.finditer(s.code) if "+" in first_argument(s.code, m.end())],
        "SQL передаётся в JDBC только готовыми константами")
verdict("Только PreparedStatement",
        [s.name for s in main_sources if CREATE_STATEMENT.search(s.code)],
        "createStatement( не используется")

# Пароли задаются только в .env: в коде, настройках и compose-файле их нет.
# В отчёт попадает только имя ключа, само значение не выводится.
password_hits = [f"{s.name}: литерал пароля" for s in main_sources if JAVA_PASSWORD.search(s.text)]
properties = RESOURCES / "application.properties"
if properties.exists():
    password_hits += [f"application.properties: {m.group(1)}"
                      for m in PROPERTY_PASSWORD.finditer(properties.read_text(encoding="utf-8"))]
compose_file = MODULE / "docker-compose.yml"
if compose_file.exists():
    password_hits += [f"docker-compose.yml: {m.group(1)}"
                      for m in COMPOSE_PASSWORD.finditer(compose_file.read_text(encoding="utf-8"))]
env_example = MODULE / ".env.example"
if env_example.exists() and not PLACEHOLDER.search(env_example.read_text(encoding="utf-8")):
    password_hits.append(".env.example: POSTGRES_PASSWORD должен быть REPLACE_ME")
verdict("Нет захардкоженных паролей", password_hits, "пароли берутся только из .env и окружения")
verdict("Нет TODO/FIXME/заглушек",
        [s.name for s in main_sources if STUBS.search(s.text)],
        "TODO, FIXME и UnsupportedOperationException не найдены")

# Размеры классов и методов (ограничение ТЗ).
verdict(f"Класс ≤ {MAX_CLASS_LINES} строк",
        [f"{s.name} ({len(s.text.splitlines())} строк)" for s in main_sources
         if len(s.text.splitlines()) > MAX_CLASS_LINES],
        f"проверено {len(main_sources)} файлов")
verdict(f"Метод ≤ {MAX_METHOD_LINES} строк",
        [problem for s in main_sources for problem in long_methods(s)],
        f"проверено {len(main_sources)} файлов")

error_code_file = BASE_PACKAGE / "error/ErrorCode.java"
if error_code_file.exists():
    known = set(re.findall(r"E-\d{3}", error_code_file.read_text(encoding="utf-8")))
    verdict("Коды ошибок ErrorCode", [f"нет {code}" for code in ERROR_CODES if code not in known],
            f"все {len(ERROR_CODES)} обязательных кодов на месте")
else:
    add("Коды ошибок ErrorCode", "FAIL", "файл error/ErrorCode.java отсутствует")

# psql-скрипты и миграции Flyway должны описывать одну и ту же схему и один демо-набор.
compare_sql("Схема: sql/schema.sql = V1", MODULE / "sql/schema.sql",
            RESOURCES / "db/migration/V1__initial_schema.sql",
            lambda s: bool(DDL.match(s)) and not SCHEMA_SETUP.match(s))
compare_sql("Данные: sql/data.sql = V2", MODULE / "sql/data.sql",
            RESOURCES / "db/demo/V2__demo_data.sql",
            lambda s: bool(INSERT.match(s)))

# Первая строка compose-файла — маркер, по которому start-compose отличает актуальный файл.
if compose_file.exists():
    compose_text = compose_file.read_text(encoding="utf-8")
    compose_problems = [label for label, ok in {
        "маркер # AVIA_BOOKING_COMPOSE_V1 в первой строке": compose_text.startswith("# AVIA_BOOKING_COMPOSE_V1\n"),
        "postgres:16-alpine": "image: postgres:16-alpine" in compose_text,
        "порт 127.0.0.1:${POSTGRES_PORT:-5432}:5432": '"127.0.0.1:${POSTGRES_PORT:-5432}:5432"' in compose_text,
        "SQL не монтируется в контейнер": "docker-entrypoint-initdb.d" not in compose_text,
    }.items() if not ok]
    verdict("Docker Compose: маркер и порт", ["нет: " + p for p in compose_problems],
            "маркер, образ PostgreSQL 16 и локальный порт на месте")

# Поиск распространённых форматов секретов.
source_files = project_files()
secret_hits = []
for file in source_files:
    if file.stat().st_size >= 2_000_000:
        continue
    try:
        text = file.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        continue
    for label, pattern in SECRET_PATTERNS.items():
        if re.search(pattern, text):
            secret_hits.append(f"{label}: {file.relative_to(ROOT).as_posix()}")
add("Скан известных секретов", "PASS" if not secret_hits else "FAIL",
    "ключи/токены не найдены" if not secret_hits else "; ".join(secret_hits[:20]))

parser = argparse.ArgumentParser()
parser.add_argument("--build", action="store_true", help="run Maven tests and docker compose config")
args = parser.parse_args()

if args.build:
    mvnw = str(MODULE / "mvnw.cmd") if os.name == "nt" else "./mvnw"
    run("App Maven tests", [mvnw, "-B", "-ntp", "clean", "verify"], MODULE, 900)
    if shutil.which("docker"):
        # Без --env-file compose падает на ${VAR:?}, если локальный .env ещё не создан.
        env_file = MODULE / ".env" if (MODULE / ".env").exists() else MODULE / ".env.example"
        run("Docker Compose config",
            ["docker", "compose", "-f", "console-app/docker-compose.yml",
             "--env-file", env_file.relative_to(ROOT).as_posix(), "config", "--quiet"], ROOT, 120)
    else:
        add("Docker Compose config", "SKIP", "docker отсутствует")

# SHA-256 для исходников и конфигурации; сборка, секреты, локальные данные и сам отчёт исключаются.
manifest_lines = []
for file in source_files:
    digest = hashlib.sha256(file.read_bytes()).hexdigest()
    manifest_lines.append(f"{digest}  {file.relative_to(ROOT).as_posix()}")
MANIFEST.write_text("\n".join(manifest_lines) + "\n", encoding="utf-8")

passed = sum(c.status == "PASS" for c in checks)
failed = sum(c.status == "FAIL" for c in checks)
skipped = sum(c.status == "SKIP" for c in checks)
lines = [
    "# Проверка проекта", "",
    f"Результат: **PASS: {passed}; FAIL: {failed}; SKIP: {skipped}.**", "",
    "`SKIP` означает, что в текущей среде нет нужного инструмента или SDK.", "",
    "| Проверка | Статус | Детали | Время |", "|---|---:|---|---:|",
]
for c in checks:
    icon = {"PASS": "✅ PASS", "FAIL": "❌ FAIL", "SKIP": "⚪ SKIP"}[c.status]
    safe_details = c.details.replace("|", "\\|")
    lines.append(f"| {c.name} | {icon} | {safe_details} | {c.seconds:.1f}s |")
lines += ["", "## Контрольные суммы", "",
          f"`SOURCE_MANIFEST.sha256` содержит {len(manifest_lines)} хешей исходников и конфигурации.", ""]
REPORT.write_text("\n".join(lines), encoding="utf-8")
print(f"PASS={passed} FAIL={failed} SKIP={skipped}; report={REPORT}")
sys.exit(1 if failed else 0)
