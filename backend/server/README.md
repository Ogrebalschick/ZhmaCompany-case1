# Модуль `:server`

Точка входа: Ktor-сервер, SQLite через Exposed, очередь ходов чата и очередь картинок. Единственный компонент, который пишет в базу: на каждое сообщение вызывает `AgentTurn` из `:ai` и, если в ответе есть `CommitRequest`, выполняет его (`applyCommit` в `service/Store.kt`): сохранение, тег, место, сегмент, модерация, одобрение, отклонение, запуск, остановка. Настройки — `.env` в корне (`wiring/AppConfig.kt`), журнал запросов к модели — `data/llm.log`.

Вызывает экран (`frontend`) по HTTP. Каждый запрос к `/api` несёт `X-User` и `X-Role` (`campaignManager` или `campaignModerator`), кроме `/api/health` и `/api/images/*`. Картинки лежат файлами в `data/images/`; пока кампания не прошла модерацию, `GET /api/images/{id}` накладывает водяной знак `WATERMARK_TEXT`.

Запуск: `cd backend && ./gradlew :server:run` (порт `BACKEND_PORT`, по умолчанию 8080), проверка: `./gradlew :server:ktlintCheck :server:test`. Сохранение кампании с баннером пишет тот же текст в `contents` как дерево PROMO (`role: promo` → вертикальный `linearContainer` из `text`, `image` и `button`) и ставит `creatives.content_id` на эту строку.

Ручки: `POST /api/sessions`, `GET|POST /api/sessions/{id}/messages`, `GET /api/jobs/{id}`, справочники `GET /api/topics|tags|segments|places|banner-types|channels`, кампании `GET /api/campaigns`, `GET /api/campaigns/{id}`, `/warnings`, `/audit`, `POST /confirm|to-moderation|decline|approve|activate`, картинки `GET /api/images/{id}`, `/status`, `?download=1`, `GET /api/health`.
