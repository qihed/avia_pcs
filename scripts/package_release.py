#!/usr/bin/env python3
"""Формирует ZIP-архив проекта и проверяет его состав."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parents[1]
out = root.parent / f"{root.name}.zip"
# Сборка, локальные данные и секреты в релиз не попадают.
exclude_dirs = {"target", ".idea", ".vscode", ".git", "__pycache__", "build-reports", "backups", "exports"}
exclude_names = {".env", ".DS_Store", "Thumbs.db"}
exclude_suffixes = {".log", ".zip"}


def excluded(path: Path) -> bool:
    relative = path.relative_to(root)
    if any(part in exclude_dirs for part in relative.parts[:-1]): return True
    if path.name in exclude_names or path.suffix.lower() in exclude_suffixes: return True
    # Локальные варианты .env тоже содержат пароли; шаблон .env.example входит в архив.
    return path.name.startswith(".env.") and path.name != ".env.example"


files = [path for path in root.rglob("*") if path.is_file() and not excluded(path)]

with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(files):
        archive.write(path, (Path(root.name) / path.relative_to(root)).as_posix())

with zipfile.ZipFile(out) as archive:
    names = set(archive.namelist())
    required = {
        f"{root.name}/console-app/pom.xml",
        f"{root.name}/console-app/mvnw",
        f"{root.name}/console-app/mvnw.cmd",
        f"{root.name}/console-app/docker-compose.yml",
        f"{root.name}/console-app/.env.example",
        f"{root.name}/console-app/src/main/resources/db/migration/V1__initial_schema.sql",
        f"{root.name}/console-app/src/main/resources/db/demo/V2__demo_data.sql",
        f"{root.name}/README.md",
        f"{root.name}/docs/QUICKSTART.md",
    }
    missing = sorted(required - names)
    if missing: raise SystemExit("ZIP is incomplete: " + ", ".join(missing))
    bad = archive.testzip()
    if bad: raise SystemExit("Corrupt ZIP entry: " + bad)

digest = hashlib.sha256(out.read_bytes()).hexdigest()
sha = out.with_suffix(out.suffix + ".sha256")
sha.write_text(f"{digest}  {out.name}\n", encoding="utf-8")
print(f"Created {out} ({len(files)} files), SHA-256 {digest}")
