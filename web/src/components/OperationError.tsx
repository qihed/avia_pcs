import { ApiError } from "../api/client";
export function OperationError({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="alert" role="alert">
      <p>
        {error instanceof Error
          ? error.message
          : "Не удалось выполнить операцию"}
      </p>
      {error instanceof ApiError &&
        Object.entries(error.fieldErrors).map(([name, text]) => (
          <p key={name}>
            {name}: {text}
          </p>
        ))}
      {error instanceof ApiError && error.code === "VERSION_CONFLICT" && (
        <p>
          Перечитайте актуальные данные, проверьте запись и повторите изменение.
        </p>
      )}
    </div>
  );
}
