# Модуль `:server`

## Что это

Точка входа процесса. Ktor на порту 8080, SQLite через Exposed, справочники и сохранение кампании. Разбор реплики не пишет: на сообщение чата вызывает `AgentTurn` из `:ai`.

База: `backend/data/campaigns.db`. Каталог с файлом базы в git не входит. При первом старте сидируются темы, теги, сегменты, места, типы баннеров и каналы. Кампании 101–115 не загружаются.

`weight` по умолчанию 0, `initialCtr` по умолчанию 15, если в черновике пусто. Новая кампания: `DRAFT`, `published=false`, автор из `X-User`.

## Кто вызывает

Его вызывает человек через экран или прямой HTTP. Сам модуль вызывает `:ai` (`AgentTurn`, модели, `LlmClient`). Другие модули его не подключают.

Клиент модели выбирает `llmClient()` в `src/main/kotlin/ru/zhma/campaign/server/wiring/Clients.kt`.

- Заданы `OPENAI_BASE_URL` и `OPENAI_MODEL` — `OpenAiCompatibleClient`. `OPENAI_API_KEY` необязателен.
- Иначе — `MockLlmClient`, без сети.

Ожидание одного job — 100 секунд, чтобы успели два вызова модели. Ошибка помечает job как `FAILED` и процесс не останавливает.

Полный список ручек, заголовки и коды ошибок — в корневом `README.md`.

## Как запустить

Нужен JDK 17 или новее. Из каталога `backend`. На Windows `gradlew.bat`, на Unix `./gradlew`.

```bat
set OPENAI_BASE_URL=http://100.104.60.3:9292/v1
set OPENAI_MODEL=qwen3.8-27b-fast
gradlew.bat :server:run
```

Без этих двух переменных поднимается тот же сервер на моке. Рядом запускают фронт, см. `frontend/README.md`.

## Как проверить

```bat
gradlew.bat :server:ktlintCheck :server:test
```

`ktlintCheck` проверяет стиль, `:server:test` компилирует и гоняет тесты. Поправить формат: `gradlew.bat :server:ktlintFormat`. Предупреждения компилятора считаются ошибками.
