# Добавленная часть: аудит, аналитика, массовые операции и CSV

Все исходные файлы проекта оставлены без изменений. Добавлены только новые файлы.

## Backend
- `CorrelationIdFilter` — создаёт/пробрасывает `X-Correlation-Id`.
- `AuditService`, `AuditController`, `AuditEventResponse` — запись и просмотр журнала аудита.
- `AnalyticsService`, `AnalyticsController`, `AnalyticsResponse` — агрегированная аналитика.
- `BookingManagementService`, `BookingManagementController` и DTO — массовое изменение броней, CSV export/preview/apply.

## Web
- `analytics.ts`, `AnalyticsPage.tsx` — API и страница аналитики.
- `audit.ts`, `AuditPage.tsx` — API и страница аудита.
- `bookingManagement.ts`, `BookingBulkActionsPanel.tsx` — массовые операции и CSV.

## Важно
Чтобы не изменять существующие файлы команды, новые React-компоненты НЕ добавлены в существующий `routes.tsx` и общий экран броней.
После согласования интеграции с командой `AnalyticsPage` и `AuditPage` можно подключить в `routes.tsx`, а `BookingBulkActionsPanel` встроить в общий экран броней.

## Проверка
Из корня проекта:

```bash
cd backend
./mvnw test
cd ../web
npm install
npm run build
```

Проверка того, что исходные файлы не менялись:

```bash
git status --short
```

До коммита должны быть только строки `??` с новыми файлами. Строк `M` и `D` быть не должно.
