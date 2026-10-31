# Структура фронтенда

Обновлено: на пустой ленте два примера. Свой бриф «Баня и аквапарк» не угадывает возраст и тему. Бриф про вклад остаётся.

Статус: экран чата готов на локальных ответах. `npm run dev` в `frontend/` открывает `http://localhost:5173`. Следующая правка, если она понадобится, стыкует тот же клиент с живым `POST /api/sessions` (выключить мок через `.env.local`).

Как работать с этим файлом: прочитать его, открыть только файлы из «Куда идти», после правки обновить статус, дерево, контракт, мок и «Куда идти».

## Контракт

Заголовки каждого запроса: `X-User: ivanov.p`, `X-Role: campaignManager | campaignModerator`. Смена роли сессию не стирает.

- `POST /api/sessions` → `{ sessionId }`
- `GET /api/sessions/{id}/messages` → массив `{ role, text, blocks? }` или `{ messages: [...] }`
- `POST /api/sessions/{id}/messages` тело `{ text }` → `202 { jobId }`
- `GET /api/jobs/{jobId}` → `{ status: PENDING | DONE | FAILED, result, error }`

`result`: `{ text, blocks }`. У блока есть `type`:

- `campaign_draft` — `draft`, `missing: string[]`, `readyToSave`. Кнопка «Сохранить» только при `readyToSave`, шлёт `подтверждаю`.
- `quick_replies` — `options: string[]`, клик шлёт текст кнопки.
- `entity_proposal` — `proposal: { kind, name, code, description }`. `TAG` — «Создать тег», `PLACE` — «Создать место». Кнопки шлют `подтверждаю` и `отмена`.
- `creative_preview` — `banner`. `STORY` узкая рамка, иначе широкая. Текст рисует вёрстка, не картинка.
- `campaign_list` — `items` с именем, `state`, датами и отдельным признаком `published`.
- `warnings` — жёлтые строки, ввод не блокируют.
- любой другой `type` — компонент ничего не рисует, текст сообщения остаётся.

Коды `missing`: `name`, `startDate`, `startTime`, `topic`, `audienceType`, `places`, `segments`. Пустые поля показываются как «—», вес и CTR не подставляются.

Пока job `PENDING`, поле заблокировано и виден статус «Собираю ответ». Опрос раз в секунду, не дольше 45 попыток. `FAILED` и ошибка сети пишутся в ленту как сообщение ассистента. Если сессия не открылась, шапка пишет «Сессия не создана», а поле остаётся закрытым.

## Мок

Включён, если `VITE_USE_MOCK=true` (так в `.env.development`). Класс `MockChatClient`, сессия `mock-session`. Первый опрос job — `PENDING`, второй — `DONE` или `FAILED`.

- фраза с «вклад» и «москв» — черновик, `readyToSave: false`, `missing: ["startTime"]`
- фраза с «баня» или «аквапарк» и «женщин» или «старше 35» — черновик «Баня и аквапарк»: `FEMALE`, `CITY_MSK`, место `1`, без возраста и темы, `missing: ["segments", "topic", "startDate", "startTime"]`. Кнопки «Возраст 46–60» и «Возраст 26–45», затем «Образ жизни» (`LIFESTYLE`) и «Событие» (`EVENT`). Дату `YYYY-MM-DD` и время `HH:mm` копирует из реплики. Пока дыры есть, `подтверждаю` не сохраняет и не подменяет черновик вкладом
- ровно `подтверждаю` на готовом черновике вклада — время `10:00` и кнопка сохранения; повторное подтверждение ставит `savedCampaignId: 201`. На собранном своём брифе то же сохранение, но поля бани остаются
- ровно `отмена` — текст, что предложение снято, черновик без изменений
- `ошибка` — на втором опросе `FAILED`
- `предупреж` — жёлтая плашка
- `список` или `покажи кампании` — две строки списка
- `тег` — предложение тега
- `место` вместе с `созда` или `нов` — предложение места
- `вариант` — быстрые ответы
- `смарт` — широкое превью
- `баннер`, `креатив` или `сторис` — узкая история
- `неизвестн` — текст и блок `future_widget`, который лента пропускает

Стартовые фразы пустой ленты: «Кампания «Баня и аквапарк» для женщин старше 35 в Москве, промо на главном» и «Кампания по вкладу для клиентов в Москве, сторис на главном экране».

## Дерево

Один визуальный кусок — один файл. Стили рядом, `*.module.css`. `App.tsx` только собирает экран.

- `frontend/src/main.tsx` — точка входа
- `frontend/src/App.tsx` — шапка, лента, поле, боковой черновик
- `frontend/src/styles/global.css` — фон, шрифт, переменные цвета
- `frontend/src/styles/controls.module.css` — общая кнопка
- `frontend/src/api/types.ts` — роли, черновик, блоки, job
- `frontend/src/api/client.ts` — интерфейс `ChatClient`
- `frontend/src/api/createClient.ts` — мок или HTTP
- `frontend/src/api/httpClient.ts` — fetch на `/api`
- `frontend/src/api/headers.ts` — `X-User` и `X-Role`
- `frontend/src/api/guards.ts` — разбор JSON и проверка `type`
- `frontend/src/api/errors.ts` — текст ошибки сети
- `frontend/src/mock/fixtures.ts` — черновик вклада, черновик бани, узнавание фраз, коды возраста и темы
- `frontend/src/mock/mockClient.ts` — сессия, job из двух опросов, ветки вклада и своего брифа
- `frontend/src/chat/useChat.ts` — сессия, отправка, опрос
- `frontend/src/chat/selectors.ts` — последний черновик и последние быстрые ответы
- `frontend/src/chat/phrases.ts` — `подтверждаю`, `отмена`, логин, две стартовые фразы (`OWN_BRIEF`, `STARTER_BRIEF`)
- `frontend/src/labels/fields.ts` — подписи полей, аудитории и `missing`
- `frontend/src/labels/states.ts` — ярлык того же `state` и отдельно `published`
- `frontend/src/labels/catalog.ts` — имена сегментов, мест и тегов из брифа
- `frontend/src/components/layout/AppShell.tsx` — колонки
- `frontend/src/components/layout/HeaderBar.tsx` — пользователь и сессия
- `frontend/src/components/layout/RoleSelect.tsx` — две роли
- `frontend/src/components/thread/MessageList.tsx` — прокрутка ленты
- `frontend/src/components/thread/EmptyThread.tsx` — пустая лента: сначала свой бриф (`starter-own-brief`), ниже бриф про вклад (`starter-brief`)
- `frontend/src/components/thread/UserMessage.tsx` — реплика пользователя
- `frontend/src/components/thread/AssistantMessage.tsx` — текст ассистента
- `frontend/src/components/thread/BlockList.tsx` — выбор блока по `type`
- `frontend/src/components/composer/Composer.tsx` — Enter и Shift+Enter
- `frontend/src/components/composer/PendingStatus.tsx` — «Собираю ответ»
- `frontend/src/components/composer/QuickReplyBar.tsx` — те же ответы над полем
- `frontend/src/components/blocks/CampaignDraftCard.tsx` — поля черновика и «Сохранить»
- `frontend/src/components/blocks/DraftField.tsx` — одна строка поля
- `frontend/src/components/blocks/MissingList.tsx` — «осталось уточнить»
- `frontend/src/components/blocks/QuickReplies.tsx` — ряд кнопок
- `frontend/src/components/blocks/EntityProposalCard.tsx` — тег или место
- `frontend/src/components/blocks/WarningsBanner.tsx` — жёлтая плашка
- `frontend/src/components/blocks/CampaignList.tsx` — список
- `frontend/src/components/blocks/CampaignListRow.tsx` — имя, состояние, даты, показ
- `frontend/src/components/blocks/CreativePreview.tsx` — подпись и выбор рамки
- `frontend/src/components/blocks/StoryFrame.tsx` — узкая история
- `frontend/src/components/blocks/SmartBannerFrame.tsx` — широкий баннер
- `frontend/src/components/blocks/UnknownBlock.tsx` — пустая заглушка
- `frontend/src/components/draft/DraftRail.tsx` — закреплённый последний черновик
- `frontend/vite.config.ts` — порт 5173 и прокси `/api` → `http://localhost:8080`
- `frontend/README.md` — как запустить

## Куда идти

- Отправка, опрос, блокировка поля: `frontend/src/chat/useChat.ts`, `frontend/src/components/composer/Composer.tsx`, `frontend/src/components/composer/PendingStatus.tsx`
- Заголовки и роль: `frontend/src/api/headers.ts`, `frontend/src/components/layout/HeaderBar.tsx`, `frontend/src/components/layout/RoleSelect.tsx`
- Форма JSON и новый `type`: `frontend/src/api/types.ts`, `frontend/src/api/guards.ts`, `frontend/src/components/thread/BlockList.tsx`
- HTTP и ошибка сети: `frontend/src/api/httpClient.ts`, `frontend/src/api/errors.ts`
- Фразы мока и свой бриф: `frontend/src/mock/mockClient.ts`, `frontend/src/mock/fixtures.ts`, `frontend/src/chat/phrases.ts`, `frontend/src/components/thread/EmptyThread.tsx`
- Карточка черновика и «Сохранить»: `frontend/src/components/blocks/CampaignDraftCard.tsx`, `frontend/src/components/blocks/MissingList.tsx`, `frontend/src/components/draft/DraftRail.tsx`
- Подписи полей и статусов: `frontend/src/labels/fields.ts`, `frontend/src/labels/states.ts`, `frontend/src/labels/catalog.ts`
- Превью баннера: `frontend/src/components/blocks/CreativePreview.tsx`, `frontend/src/components/blocks/StoryFrame.tsx`, `frontend/src/components/blocks/SmartBannerFrame.tsx`
- Быстрые ответы: `frontend/src/components/blocks/QuickReplies.tsx`, `frontend/src/components/composer/QuickReplyBar.tsx`, `frontend/src/chat/selectors.ts`
- Цвета и страница: `frontend/src/styles/global.css`, `frontend/src/components/layout/AppShell.tsx`
- Сборка экрана: `frontend/src/App.tsx`
