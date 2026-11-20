import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { hasRole } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { BookingList } from "../components/BookingList";
import { BookingTagsEditor } from "../components/BookingTagsEditor";
import { OperationError } from "../components/OperationError";
import {
  refs,
  date,
  statusNames,
  urgencyNames,
  targets,
  type Booking,
  type Passenger,
  type Flight,
  type Note,
} from "../api/booking";

type Draft = {
  passengerId: string;
  flightId: string;
  seatNumber: string;
  fareClass: string;
  price: number;
  extraBaggage: boolean;
  version?: number;
};
function fromBooking(b: Booking): Draft {
  return {
    passengerId: b.passenger.id,
    flightId: b.flight.id,
    seatNumber: b.seatNumber,
    fareClass: b.fareClass,
    price: b.price,
    extraBaggage: b.extraBaggage,
    version: b.version,
  };
}
export function BookingsPage() {
  const { user } = useAuth(),
    editor = hasRole(user, "EDITOR"),
    admin = hasRole(user, "ADMIN");
  // список (поиск, фильтры, сортировка, страницы) живёт в BookingList; reloadKey просит его перечитать данные
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<Booking | null>(null),
    [draft, setDraft] = useState<Draft | null>(null),
    [creating, setCreating] = useState(false);
  const [passengers, setPassengers] = useState<Passenger[]>([]),
    [flights, setFlights] = useState<Flight[]>([]),
    [notes, setNotes] = useState<Note[]>([]);
  const [noteText, setNoteText] = useState(""),
    [noteId, setNoteId] = useState<string | null>(null),
    [pinned, setPinned] = useState(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null);
  async function load() {
    setReloadKey((k) => k + 1);
  }
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function open(b: Booking) {
    const [latest, n] = await Promise.all([
      api<Booking>("/api/items/" + b.id),
      api<Note[]>("/api/items/" + b.id + "/notes"),
    ]);
    setSelected(latest);
    setNotes(n);
    setDraft(null);
    setCreating(false);
    resetNote();
  }
  function resetNote() {
    setNoteId(null);
    setNoteText("");
    setPinned(false);
  }
  async function edit(b?: Booking) {
    const [p, f] = await Promise.all([refs.passengers(), refs.flights()]);
    setPassengers(p);
    setFlights(f);
    setCreating(!b);
    setDraft(
      b
        ? fromBooking(b)
        : {
            passengerId: "",
            flightId: "",
            seatNumber: "",
            fareClass: "ECONOMY",
            price: 0,
            extraBaggage: false,
          },
    );
    if (!b) {
      setSelected(null);
      setNotes([]);
    }
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!draft) return;
    await run(async () => {
      const saved = await api<Booking>(
        "/api/items" + (!creating && selected ? "/" + selected.id : ""),
        { method: creating ? "POST" : "PUT", body: draft },
      );
      await open(saved);
      await load();
    });
  }
  async function mutate(suffix: string, method: string, body?: unknown) {
    if (!selected) return;
    await run(async () => {
      const b = await api<Booking>("/api/items/" + selected.id + suffix, {
        method,
        body,
      });
      await open(b);
      await load();
    });
  }
  const terminal =
    selected && ["COMPLETED", "CANCELLED", "EXPIRED"].includes(selected.status);
  const editable =
    selected &&
    !selected.archived &&
    ["CREATED", "PAID"].includes(selected.status);
  return (
    <section className="stack">
      <div className="toolbar">
        <h1>Бронирования</h1>
        <span className="spacer" />
        {editor && (
          <button
            className="btn primary"
            disabled={busy}
            onClick={() => void run(() => edit())}
          >
            Новая бронь
          </button>
        )}
        <button
          className="btn ghost"
          disabled={busy}
          onClick={() =>
            void run(async () => {
              if (selected) await open(selected);
              else setDraft(null);
              await load();
            })
          }
        >
          Обновить данные
        </button>
      </div>
      <OperationError error={error} />
      {draft && (
        <form className="card stack" onSubmit={save}>
          <h2>{creating ? "Новая бронь" : "Изменение брони"}</h2>
          <fieldset disabled={busy} className="form-grid">
            <label className="field">
              <span>Пассажир</span>
              <select
                required
                disabled={!creating}
                value={draft.passengerId}
                onChange={(e) =>
                  setDraft({ ...draft, passengerId: e.target.value })
                }
              >
                <option value="">Выберите пассажира</option>
                {passengers.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.lastName} {p.firstName} · {p.documentNumber}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Рейс · время UTC</span>
              <select
                required
                disabled={!creating}
                value={draft.flightId}
                onChange={(e) => {
                  const f = flights.find((f) => f.id === e.target.value);
                  setDraft({
                    ...draft,
                    flightId: e.target.value,
                    price: f?.basePrice ?? 0,
                  });
                }}
              >
                <option value="">Выберите рейс</option>
                {flights
                  .filter(
                    (f) =>
                      !creating ||
                      (f.active &&
                        ["SCHEDULED", "DELAYED"].includes(f.status) &&
                        new Date(f.departureTime + "Z").getTime() > Date.now()),
                  )
                  .map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.flightNumber} · {f.departureAirport} →{" "}
                      {f.arrivalAirport} · {date(f.departureTime)}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              <span>Место (например, 12A)</span>
              <input
                required
                pattern="[1-9][0-9]?[A-F]"
                maxLength={3}
                value={draft.seatNumber}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    seatNumber: e.target.value.toUpperCase(),
                  })
                }
              />
            </label>
            <label className="field">
              <span>Класс</span>
              <select
                value={draft.fareClass}
                onChange={(e) =>
                  setDraft({ ...draft, fareClass: e.target.value })
                }
              >
                <option value="ECONOMY">Эконом</option>
                <option value="COMFORT">Комфорт</option>
                <option value="BUSINESS">Бизнес</option>
              </select>
            </label>
            <label className="field">
              <span>Цена, ₽</span>
              <input
                required
                type="number"
                min="0.01"
                max="99999999.99"
                step="0.01"
                value={draft.price}
                onChange={(e) =>
                  setDraft({ ...draft, price: Number(e.target.value) })
                }
              />
            </label>
            <label className="field">
              <span>Дополнительный багаж</span>
              <input
                type="checkbox"
                checked={draft.extraBaggage}
                onChange={(e) =>
                  setDraft({ ...draft, extraBaggage: e.target.checked })
                }
              />
            </label>
          </fieldset>
          <p className="muted">
            Для нового рейса или пассажира создайте отдельную бронь. Места: ряды
            по шесть кресел A–F.
          </p>
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
      {selected && (
        <article className="card stack">
          <div className="toolbar">
            <h2>Бронь {selected.bookingCode}</h2>
            <span className="badge">{statusNames[selected.status]}</span>
            <span className="spacer" />
            <button
              className="btn ghost"
              disabled={busy}
              onClick={() => {
                setSelected(null);
                setDraft(null);
              }}
            >
              Закрыть
            </button>
          </div>
          <dl className="details">
            <div>
              <dt>Пассажир</dt>
              <dd>
                {selected.passenger.lastName} {selected.passenger.firstName}
              </dd>
            </div>
            <div>
              <dt>Рейс</dt>
              <dd>
                {selected.flight.flightNumber} ·{" "}
                {selected.flight.departureAirport} →{" "}
                {selected.flight.arrivalAirport}
              </dd>
            </div>
            <div>
              <dt>Вылет (UTC)</dt>
              <dd>{date(selected.flight.departureTime)}</dd>
            </div>
            <div>
              <dt>Место и класс</dt>
              <dd>
                {selected.seatNumber} · {selected.fareClass}
              </dd>
            </div>
            <div>
              <dt>Стоимость</dt>
              <dd>{selected.price.toLocaleString("ru-RU")} ₽</dd>
            </div>
            <div>
              <dt>Дополнительный багаж</dt>
              <dd>{selected.extraBaggage ? "Да" : "Нет"}</dd>
            </div>
            <div>
              <dt>Срочность</dt>
              <dd>{urgencyNames[selected.urgencyLevel]}</dd>
            </div>
            <div>
              <dt>Обновлено (UTC)</dt>
              <dd>{date(selected.updatedAt)}</dd>
            </div>
          </dl>
          <BookingTagsEditor
            booking={selected}
            canEdit={editor && !selected.archived}
            busy={busy}
            run={run}
            onSaved={async (b) => {
              await open(b);
              await load();
            }}
          />
          {editor && (
            <div className="toolbar">
              {editable && (
                <button
                  className="btn ghost"
                  disabled={busy}
                  onClick={() => void run(() => edit(selected))}
                >
                  Изменить
                </button>
              )}
              {!selected.archived &&
                targets[selected.status].map((s) => (
                  <button
                    key={s}
                    className="btn ghost"
                    disabled={busy}
                    onClick={() => {
                      if (
                        s !== "CANCELLED" ||
                        window.confirm("Отменить бронирование?")
                      )
                        void mutate("/status", "PATCH", {
                          status: s,
                          version: selected.version,
                        });
                    }}
                  >
                    {statusNames[s]}
                  </button>
                ))}
              {terminal && !selected.archived && (
                <button
                  className="btn ghost"
                  disabled={busy}
                  onClick={() =>
                    void mutate("?version=" + selected.version, "DELETE")
                  }
                >
                  В архив
                </button>
              )}
              {selected.archived && (
                <button
                  className="btn ghost"
                  disabled={busy}
                  onClick={() =>
                    void mutate("/restore", "POST", {
                      version: selected.version,
                    })
                  }
                >
                  Восстановить
                </button>
              )}
              {selected.archived && admin && (
                <button
                  className="btn danger"
                  disabled={busy}
                  onClick={() => {
                    if (
                      window.confirm(
                        "Окончательно удалить бронь и все её заметки? Это действие необратимо.",
                      )
                    )
                      void run(async () => {
                        await api(
                          "/api/items/" +
                            selected.id +
                            "/permanent?version=" +
                            selected.version,
                          { method: "DELETE" },
                        );
                        setSelected(null);
                        setNotes([]);
                        await load();
                      });
                  }}
                >
                  Удалить навсегда
                </button>
              )}
            </div>
          )}
          <h2>Заметки</h2>
          {!notes.length && <p className="muted">Заметок пока нет</p>}
          {notes.map((n) => (
            <section key={n.id} className="note">
              <div className="toolbar">
                <strong>{n.authorName}</strong>
                <small className="muted">{date(n.createdAt)} UTC</small>
                {n.pinned && <span className="badge">Закреплено</span>}
              </div>
              <p className="note-text">{n.text}</p>
              {editor &&
                !selected.archived &&
                (n.authorId === user?.id || admin) && (
                  <div className="toolbar">
                    <button
                      className="btn ghost"
                      disabled={busy}
                      onClick={() => {
                        setNoteId(n.id);
                        setNoteText(n.text);
                        setPinned(n.pinned);
                      }}
                    >
                      Изменить
                    </button>
                    <button
                      className="btn danger"
                      disabled={busy}
                      onClick={() => {
                        if (window.confirm("Удалить заметку?"))
                          void run(async () => {
                            await api(
                              "/api/items/" + selected.id + "/notes/" + n.id,
                              { method: "DELETE" },
                            );
                            await open(selected);
                          });
                      }}
                    >
                      Удалить
                    </button>
                  </div>
                )}
            </section>
          ))}
          {editor && !selected.archived && (
            <form
              className="stack"
              onSubmit={(e) => {
                e.preventDefault();
                void run(async () => {
                  await api(
                    "/api/items/" +
                      selected.id +
                      "/notes" +
                      (noteId ? "/" + noteId : ""),
                    {
                      method: noteId ? "PUT" : "POST",
                      body: { text: noteText, pinned },
                    },
                  );
                  await open(selected);
                });
              }}
            >
              <label className="field">
                <span>{noteId ? "Изменение заметки" : "Новая заметка"}</span>
                <textarea
                  required
                  maxLength={2000}
                  rows={3}
                  disabled={busy}
                  value={noteText}
                  onChange={(e) => setNoteText(e.target.value)}
                />
              </label>
              <label>
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={pinned}
                  onChange={(e) => setPinned(e.target.checked)}
                />{" "}
                Закрепить
              </label>
              <div className="toolbar">
                <button
                  className="btn primary"
                  disabled={busy || !noteText.trim()}
                >
                  Сохранить заметку
                </button>
                {noteId && (
                  <button
                    type="button"
                    className="btn ghost"
                    disabled={busy}
                    onClick={resetNote}
                  >
                    Отмена
                  </button>
                )}
              </div>
            </form>
          )}
        </article>
      )}
      <BookingList
        busy={busy}
        reloadKey={reloadKey}
        selectedId={selected?.id}
        editor={editor}
        onOpen={(b) => void run(() => open(b))}
        onChanged={() => {
          if (selected) void run(() => open(selected));
        }}
      />
    </section>
  );
}
