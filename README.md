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

**Windows:** дважды щёлкните `start.bat` в корне. Скрипт:
1. создаст `.env` из `.env.example`, если его нет;
2. запустит сервер (`gradlew.bat :server:run`) в отдельном окне;
3. при первом запуске сделает `npm install`;
4. дождётся, пока сервер ответит (первая сборка — 2–5 минут), запустит экран и откроет браузер.

Нужны JDK 17+ (`java` в PATH) и Node.js.

**Все настройки — в `.env` в корне** (образец с комментариями — `.env.example`):
- `LLM_HOST`, `LLM_PORT`, `LLM_PATH` или `LLM_BASE_URL` целиком — адрес llama.cpp;
- `LLM_MODEL` — основная модель, `IMAGE_MODEL` — модель картинок (адрес по умолчанию тот же, что у LLM);
- `BACKEND_PORT`, `FRONTEND_PORT` — порты сервера и экрана;
- `LLM_MOCK=true` — работать без модели.

Переменные окружения важнее файла. Сервер ищет `.env` от текущей папки вверх.

Вручную: `cd backend && gradlew.bat :server:run` (на Unix — `./gradlew :server:run`), затем `cd frontend && npm install && npm run dev`.
Экран: `http://localhost:<FRONTEND_PORT>`.

Если в чате ошибка 500 или «Жду, пока запустится сервер…» — сервер ещё не поднялся или упал: смотрите окно BACKEND.

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
