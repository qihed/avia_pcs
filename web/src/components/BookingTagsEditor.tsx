import { useEffect, useState, type FormEvent } from 'react'
import type { Booking } from '../api/booking'
import { bookingSearchApi, type TagUsage } from '../api/bookingSearch'

/** Теги в карточке брони: просмотр для всех, добавление и удаление — редактору у неархивной брони. */
export function BookingTagsEditor({
  booking,
  canEdit,
  busy,
  run,
  onSaved,
}: {
  booking: Booking
  canEdit: boolean
  busy: boolean
  /** Обёртка карточки: блокирует кнопки и показывает ошибку (в том числе VERSION_CONFLICT) */
  run: (action: () => Promise<void>) => Promise<void>
  onSaved: (booking: Booking) => Promise<void>
}) {
  const [value, setValue] = useState('')
  const [known, setKnown] = useState<TagUsage[]>([])

  useEffect(() => {
    if (canEdit) bookingSearchApi.tags().then(setKnown, () => setKnown([]))
  }, [canEdit, booking.id])

  function save(tags: string[]) {
    return run(async () => {
      await onSaved(await bookingSearchApi.setTags(booking, tags))
      setValue('')
    })
  }

  function add(e: FormEvent) {
    e.preventDefault()
    const tag = value.trim().replace(/\s+/g, ' ').toLowerCase()
    if (!tag || booking.tags.includes(tag)) {
      setValue('')
      return
    }
    void save([...booking.tags, tag])
  }

  return (
    <div className="stack tags-editor">
      <h2>Теги</h2>
      <div className="chips">
        {!booking.tags.length && <span className="muted">Тегов нет</span>}
        {booking.tags.map((t) => (
          <span key={t} className="chip tag">
            {t}
            {canEdit && (
              <button
                type="button"
                className="chip-remove"
                aria-label={'Убрать тег ' + t}
                disabled={busy}
                onClick={() => void save(booking.tags.filter((x) => x !== t))}
              >
                ×
              </button>
            )}
          </span>
        ))}
      </div>
      {canEdit && booking.tags.length < 10 && (
        <form className="toolbar" onSubmit={add}>
          <input
            className="bulk-input"
            list="card-known-tags"
            maxLength={40}
            placeholder="Новый тег, например vip"
            value={value}
            disabled={busy}
            onChange={(e) => setValue(e.target.value)}
            aria-label="Новый тег"
          />
          <datalist id="card-known-tags">
            {known
              .filter((t) => !booking.tags.includes(t.tag))
              .map((t) => (
                <option key={t.tag} value={t.tag} />
              ))}
          </datalist>
          <button className="btn ghost" disabled={busy || !value.trim()}>
            Добавить тег
          </button>
        </form>
      )}
    </div>
  )
}
