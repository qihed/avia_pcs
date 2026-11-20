import { useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { hasRole } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { OperationError } from "../components/OperationError";
import { date } from "../api/booking";

type Row = Record<string, string | number | boolean | undefined> & {
  id?: string;
};
type Field = {
  name: string;
  label: string;
  type?: string;
  options?: string[];
  max?: number;
  pattern?: string;
  required?: boolean;
};
const passengerFields: Field[] = [
  { name: "lastName", label: "Фамилия", max: 60 },
  { name: "firstName", label: "Имя", max: 60 },
  { name: "middleName", label: "Отчество", max: 60, required: false },
  { name: "birthDate", label: "Дата рождения", type: "date" },
  {
    name: "documentType",
    label: "Тип документа",
    options: [
      "PASSPORT_RF",
      "INTERNATIONAL_PASSPORT",
      "BIRTH_CERTIFICATE",
      "FOREIGN_DOCUMENT",
    ],
  },
  { name: "documentNumber", label: "Номер документа", max: 20 },
  { name: "email", label: "E-mail", type: "email", max: 120 },
  { name: "phone", label: "Телефон", type: "tel", max: 20 },
];
const flightFields: Field[] = [
  {
    name: "flightNumber",
    label: "Номер рейса",
    max: 8,
    pattern: "[A-Z0-9]{2,8}",
  },
  { name: "airline", label: "Авиакомпания", max: 60 },
  {
    name: "departureAirport",
    label: "Аэропорт вылета (IATA)",
    max: 3,
    pattern: "[A-Z]{3}",
  },
  {
    name: "arrivalAirport",
    label: "Аэропорт прилёта (IATA)",
    max: 3,
    pattern: "[A-Z]{3}",
  },
  { name: "departureTime", label: "Вылет (UTC)", type: "datetime-local" },
  { name: "arrivalTime", label: "Прилёт (UTC)", type: "datetime-local" },
  { name: "aircraftType", label: "Самолёт", max: 40 },
  { name: "totalSeats", label: "Количество мест", type: "number", max: 594 },
  {
    name: "basePrice",
    label: "Базовая цена, ₽",
    type: "number",
    max: 99999999.99,
  },
  {
    name: "status",
    label: "Статус рейса",
    options: ["SCHEDULED", "DELAYED", "DEPARTED", "ARRIVED", "CANCELLED"],
  },
  { name: "active", label: "Доступен для бронирования", type: "checkbox" },
];
export function ReferencesPage({ kind }: { kind: "passengers" | "flights" }) {
  const { user } = useAuth();
  const admin = hasRole(user, "ADMIN");
  const [rows, setRows] = useState<Row[]>([]),
    [draft, setDraft] = useState<Row | null>(null),
    [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null);
  const fields = kind === "passengers" ? passengerFields : flightFields;
  const url = "/api/references/" + kind;
  async function load() {
    setLoading(true);
    try {
      setRows(await api<Row[]>(url));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    let live = true;
    setLoading(true);
    api<Row[]>(url)
      .then((data) => {
        if (live) setRows(data);
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [url]);
  function create() {
    setError(null);
    setDraft(
      kind === "passengers"
        ? { documentType: "PASSPORT_RF" }
        : {
            status: "SCHEDULED",
            active: true,
            totalSeats: 180,
            basePrice: 6400,
          },
    );
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    setBusy(true);
    setError(null);
    try {
      await api(url + (draft.id ? "/" + draft.id : ""), {
        method: draft.id ? "PUT" : "POST",
        body: draft,
      });
      setDraft(null);
      await load();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function remove(row: Row) {
    if (
      !window.confirm(
        "Удалить запись из справочника? Запись с бронями удалить нельзя.",
      )
    )
      return;
    setBusy(true);
    setError(null);
    try {
      await api(
        url +
          "/" +
          row.id +
          (kind === "flights" ? "?version=" + row.version : ""),
        { method: "DELETE" },
      );
      await load();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  const filtered = rows.filter((r) =>
    Object.values(r)
      .join(" ")
      .toLocaleLowerCase()
      .includes(query.toLocaleLowerCase()),
  );
  return (
    <section className="stack">
      <div className="toolbar">
        <h1>{kind === "passengers" ? "Пассажиры" : "Рейсы"}</h1>
        <span className="spacer" />
        {admin && (
          <button className="btn primary" disabled={busy} onClick={create}>
            Добавить
          </button>
        )}
        <button
          className="btn ghost"
          disabled={busy || loading}
          onClick={() => {
            setDraft(null);
            setError(null);
            void load().catch(setError);
          }}
        >
          Обновить данные
        </button>
      </div>
      <OperationError error={error} />
      {draft && (
        <form className="card stack" onSubmit={save}>
          <h2>{draft.id ? "Изменение записи" : "Новая запись"}</h2>
          <fieldset disabled={busy} className="form-grid">
            {fields.map((f) => (
              <label className="field" key={f.name}>
                <span>{f.label}</span>
                {f.options ? (
                  <select
                    required
                    value={String(draft[f.name] ?? "")}
                    onChange={(e) =>
                      setDraft({ ...draft, [f.name]: e.target.value })
                    }
                  >
                    {f.options.map((o) => (
                      <option key={o}>{o}</option>
                    ))}
                  </select>
                ) : f.type === "checkbox" ? (
                  <input
                    type="checkbox"
                    checked={Boolean(draft[f.name])}
                    onChange={(e) =>
                      setDraft({ ...draft, [f.name]: e.target.checked })
                    }
                  />
                ) : (
                  <input
                    type={f.type ?? "text"}
                    required={f.required !== false}
                    maxLength={f.type === "number" ? undefined : f.max}
                    max={
                      f.type === "number"
                        ? f.max
                        : f.type === "date"
                          ? new Date(Date.now() - 86400000)
                              .toISOString()
                              .slice(0, 10)
                          : undefined
                    }
                    min={
                      f.type === "number"
                        ? f.name === "basePrice"
                          ? "0.01"
                          : "1"
                        : undefined
                    }
                    step={f.name === "basePrice" ? "0.01" : undefined}
                    pattern={f.pattern}
                    value={String(draft[f.name] ?? "")}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        [f.name]:
                          f.type === "number"
                            ? Number(e.target.value)
                            : e.target.value,
                      })
                    }
                  />
                )}
              </label>
            ))}
          </fieldset>
          <div className="toolbar">
            <button className="btn primary" disabled={busy}>
              Сохранить
            </button>
            <button
              type="button"
              className="btn ghost"
              disabled={busy}
              onClick={() => setDraft(null)}
            >
              Отмена
            </button>
          </div>
        </form>
      )}
      <label className="field">
        <span>Поиск по справочнику</span>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Введите имя, номер рейса или аэропорт"
        />
      </label>
      {loading ? (
        <p role="status">Загрузка…</p>
      ) : (
        <div className="card table-wrap">
          <table>
            <thead>
              <tr>
                <th>{kind === "passengers" ? "Пассажир" : "Рейс"}</th>
                <th>{kind === "passengers" ? "Документ" : "Маршрут"}</th>
                <th>{kind === "passengers" ? "Контакты" : "Вылет (UTC)"}</th>
                <th>{kind === "passengers" ? "Дата рождения" : "Состояние"}</th>
                {admin && <th>Действия</th>}
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.id}>
                  <td>
                    {kind === "passengers"
                      ? [r.lastName, r.firstName, r.middleName].join(" ")
                      : r.flightNumber}
                  </td>
                  <td>
                    {kind === "passengers"
                      ? r.documentNumber
                      : r.departureAirport + " → " + r.arrivalAirport}
                  </td>
                  <td>
                    {kind === "passengers" ? (
                      <>
                        {r.email}
                        <br />
                        {r.phone}
                      </>
                    ) : (
                      date(String(r.departureTime))
                    )}
                  </td>
                  <td>
                    {kind === "passengers" ? (
                      r.birthDate
                    ) : (
                      <>
                        {r.status}
                        <br />
                        {r.active ? "Включён" : "Отключён"}
                      </>
                    )}
                  </td>
                  {admin && (
                    <td>
                      <div className="toolbar">
                        <button
                          className="btn ghost"
                          disabled={busy}
                          onClick={() => {
                            setError(null);
                            setDraft({ ...r });
                          }}
                        >
                          Изменить
                        </button>
                        <button
                          className="btn danger"
                          disabled={busy}
                          onClick={() => void remove(r)}
                        >
                          Удалить
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {!filtered.length && <p className="empty">Записей нет</p>}
        </div>
      )}
    </section>
  );
}
