# Кампании

Чат собирает черновик рекламной кампании. Запись в базу делает только сервер, и только после явного подтверждения. Разбор реплики живёт в библиотеке `:ai`.

Нужен JDK 17 или новее (байткод 17) и Node.js для экрана.

## Что это

Три части одного контура:

- `frontend/` — экран чата, порт 5173. Промптов нет, баннер рисует вёрстка.
- `backend/` модуль `:server` — HTTP, SQLite, исполнение уже принятого действия. Порт 8080.
- `backend/` модуль `:ai` — конечный автомат диалога. HTTP и базы нет.

## Кто вызывает

Человек открывает экран. Экран ходит в `:server`. Сервер на каждое сообщение вызывает `AgentTurn` и сам пишет SQLite, если в ответе есть `commit`. Модель только достаёт JSON из реплики и коротко формулирует вопрос.

Каждый запрос к `/api` несёт `X-User` и `X-Role` (`campaignManager` или `campaignModerator`). Нет заголовка — 400. Сервер возвращает оба заголовка в ответе.

## Как запустить

Живая модель, если заданы обе переменные. Иначе сервер берёт `MockLlmClient` и сеть не нужна. Ключ не обязателен: пустой `OPENAI_API_KEY` не отправляет `Authorization`.

```bat
set OPENAI_BASE_URL=http://100.104.60.3:9292/v1
set OPENAI_MODEL=qwen3.8-27b-fast
cd backend
gradlew.bat :server:run
```

На Unix те же переменные и `./gradlew :server:run`. База создаётся сама: `backend/data/campaigns.db`. При первом старте в неё попадают справочники. Исторические кампании 101–115 не загружаются.

Фронт, прокси `/api` на `http://localhost:8080`. В разработке мок выключен (`frontend/.env.development`).

```bat
cd frontend
npm install
npm run dev
```

Экран: `http://localhost:5173`. Чтобы работать без сервера, в `frontend/.env.development.local` поставьте `VITE_USE_MOCK=true`.

Один вызов модели ждёт до 45 секунд. Ход с двумя вызовами укладывается в 100 секунд ожидания job. Исключение переводит job в `FAILED` и не останавливает процесс. Картинки модель не рисует и не смотрит: превью баннера — вёрстка, файл картинки — одноцветный PNG.

Выбор клиента: `backend/server/src/main/kotlin/ru/zhma/campaign/server/wiring/Clients.kt`, функция `llmClient()`.

## Как проверить

```bat
cd backend
gradlew.bat :ai:ktlintCheck :ai:check :server:ktlintCheck :server:test
cd ..\frontend
npm run build
```

На Unix в `backend` та же команда через `./gradlew`. Эти шаги повторяет `.github/workflows/kotlin.yml`.

## Чат

1. `POST /api/sessions` → `{ "sessionId" }`, пустой черновик.
2. `POST /api/sessions/{id}/messages` с телом `{ "text": "..." }` → `202 { "jobId" }`.
3. `GET /api/jobs/{jobId}` → `status` равен `PENDING`, `DONE` или `FAILED`. У `DONE` в `result` ответ ассистента `{ "text", "blocks" }`. У `FAILED` короткое поле `error`.
4. `GET /api/sessions/{id}/messages` — история. У ответа ассистента `blocks` уже разобраны.

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

Подробности модулей: `backend/ai/README.md`, `backend/server/README.md`, `frontend/README.md`. Карта экрана: `frontend/README.md`.
