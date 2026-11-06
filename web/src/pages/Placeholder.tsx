export function Placeholder({ title, owner }: { title: string; owner: string }) {
  return (
    <div className="card stack">
      <h2>{title}</h2>
      <p className="muted">Страница в разработке: {owner}.</p>
    </div>
  )
}
