# Экран `frontend`

Точка входа: React + TypeScript + Vite, чат менеджера и модератора. Слева — новая кампания, черновики и сохранённые кампании со статусами; в центре — диалог и меню ответа над полем ввода (`QuickReplyBar`, одиночный и множественный выбор); справа — карточка черновика со статусом кампании и признаком показа. Блоки ответа рисуются по `type`: `campaign_draft`, `quick_replies`, `creative_preview` (баннер, картинка, увеличение, скачивание), `compliance` (проверка по 38-ФЗ), `entity_proposal`, `campaign_list`, `warnings`. Промптов и логики кампании на экране нет.

Ходит только в `:server` через прокси Vite `/api` → `http://localhost:BACKEND_PORT`. Номер сессии хранится в `localStorage`, обновление страницы открывает тот же чат. `VITE_USE_MOCK=true` в `.env` включает ответы из `src/mock` без сервера.

Запуск: `cd frontend && npm install && npm run dev` (порт `FRONTEND_PORT` из корневого `.env`, по умолчанию 5173), сборка и проверка: `npm run build && npm run lint`. Под Windows проще `start.bat` в корне: он поднимет сервер и экран сам.
