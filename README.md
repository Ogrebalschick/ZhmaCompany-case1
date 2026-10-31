# Кампании: сервер

Чат собирает черновик кампании. Запись в базу делает только сервер после явного подтверждения. Разбор реплики живёт в модуле `:ai`.

Нужен JDK 17 или новее. Сборка целится в байткод 17.

## Запуск

Сервер, порт 8080:

```bat
cd backend
gradlew.bat :server:run
```

На Unix: `./gradlew :server:run`. База создаётся сама: `backend/data/campaigns.db`. При первом старте в неё попадают справочники (темы, теги, сегменты, места, типы баннеров, каналы). Исторические кампании 101–115 не загружаются.

Фронт, порт 5173, прокси `/api` на `http://localhost:8080`:

```bat
cd frontend
npm install
npm run dev
```

Подробности экрана — в `frontend/README.md`, когда он появится.

Каждый запрос к `/api` несёт заголовки `X-User` и `X-Role`. Роль — `campaignManager` или `campaignModerator`. Сервер возвращает оба заголовка в ответе. Нет заголовка — 400.

## Чат

1. `POST /api/sessions` → `{ "sessionId" }`, пустой черновик.
2. `POST /api/sessions/{id}/messages` с телом `{ "text": "..." }` → `202 { "jobId" }`.
3. `GET /api/jobs/{jobId}` → `status` равен `PENDING`, `DONE` или `FAILED`. У `DONE` в `result` ответ ассистента `{ "text", "blocks" }`. У `FAILED` короткое поле `error`.
4. `GET /api/sessions/{id}/messages` — история. У ответа ассистента `blocks` уже разобраны.

Обработка job не дольше 30 секунд. Исключение переводит job в `FAILED` и не останавливает процесс.

Модель подключается в `backend/server/src/main/kotlin/ru/zhma/campaign/server/wiring/Clients.kt`, функция `llmClient()`. Сейчас это `MockLlmClient`.

## Ручки

Справочники целиком, без пагинации:

- `GET /api/topics`
- `GET /api/tags`
- `GET /api/segments`
- `GET /api/places`
- `GET /api/banner-types`
- `GET /api/channels`

Кампании:

- `GET /api/campaigns`
- `GET /api/campaigns/{id}` — шапка, `tags` и `places` как списки id, сегменты, баннеры, контент, креативы
- `GET /api/campaigns/{id}/warnings` — `{ "errors", "warnings" }`
- `GET /api/campaigns/{id}/audit`
- `POST /api/campaigns/{id}/confirm?sessionId=` — то же сохранение, что и подтверждение в чате
- `POST /api/campaigns/{id}/to-moderation` — менеджер, из `DRAFT` или `NEEDS_WORK`
- `POST /api/campaigns/{id}/decline` — модератор, тело `{ "comment": "..." }`
- `POST /api/campaigns/{id}/approve` — модератор
- `POST /api/campaigns/{id}/activate` — менеджер, только из `APPROVED`. Заглушка выгрузки всегда успешна: `published=true`, `state=ACTIVE`

Неизвестный id — 404 и код `ENTITY_NOT_FOUND`. Ошибка валидации — 400 и `{ "errors": [{ "code", "message", "field" }] }`. Роль проверяется раньше состояния; чужая роль даёт `ROLE_FORBIDDEN`.

`weight` по умолчанию 0, `initialCtr` по умолчанию 15, если в черновике пусто. Новая кампания: `DRAFT`, `published=false`, автор из `X-User`.

Проверка:

```bat
cd backend
gradlew.bat :server:test
```
