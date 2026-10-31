# Чат сборки кампаний

Один экран: шапка с ролью, лента ответов ассистента и поле ввода. Сервер и промпты живут отдельно.

Обновлено: сохранённая кампания больше не предлагает «Сохранить». Повторное «подтверждаю» не создаёт вторую карточку.

Статус: экран чата готов на локальных ответах. `npm run dev` в этой папке открывает `http://localhost:5173`. Следующая правка, если она понадобится, стыкует тот же клиент с живым `POST /api/sessions` (выключить мок через `.env.local`).

Как работать с этим файлом: прочитать его, открыть только файлы из «Куда идти», после правки обновить статус, дерево, контракт, мок и «Куда идти». Пути ниже — от корня репозитория.

## Запуск

```bash
cd frontend
npm install
npm run dev
```

Открывается `http://localhost:5173`. Запросы на `/api` проксируются на `http://localhost:8080`.

По умолчанию в разработке включены локальные ответы (`VITE_USE_MOCK=true` в `.env.development`): сервер не нужен. Чтобы ходить в API, создайте `.env.local`:

```bash
VITE_USE_MOCK=false
```

Каждый запрос отправляет заголовки `X-User: ivanov.p` и `X-Role` (`campaignManager` или `campaignModerator`). Смена роли в шапке не создаёт новую сессию. Enter отправляет, Shift+Enter переносит строку.

## Контракт

Заголовки каждого запроса: `X-User: ivanov.p`, `X-Role: campaignManager | campaignModerator`. Смена роли сессию не стирает.

- `POST /api/sessions` → `{ sessionId }`
- `GET /api/sessions/{id}/messages` → массив `{ role, text, blocks? }` или `{ messages: [...] }`
- `POST /api/sessions/{id}/messages` тело `{ text }` → `202 { jobId }`
- `GET /api/jobs/{jobId}` → `{ status: PENDING | DONE | FAILED, result, error }`

`result`: `{ text, blocks }`. У блока есть `type`:

- `campaign_draft` — `draft`, `missing: string[]`, `readyToSave`, необязательный `choices`. Кнопка «Сохранить» только при `readyToSave`, шлёт `подтверждаю`. Коды сегментов и id мест остаются в `draft`. `choices` объясняет уже выбранные коды и в кампанию не сохраняется.
- `quick_replies` — `options: string[]`. Клик шлёт строку как есть. Если это код сегмента, темы или id места, на кнопке подпись из справочника, например «Возраст 46–60 (AGE_46_60)», а уходит `AGE_46_60`. Незнакомая строка пишется на кнопке целиком. Над полем только кнопки последнего ответа. Пока job в `PENDING`, они заблокированы.
- `entity_proposal` — `proposal: { kind, name, code, description }`. `TAG` — «Создать тег», `PLACE` — «Создать место». Кнопки шлют `подтверждаю` и `отмена`.
- `creative_preview` — `banner`. `STORY` узкая рамка, иначе широкая. Текст рисует вёрстка, не картинка.
- `campaign_list` — `items` с именем, `state`, датами и отдельным признаком `published`.
- `warnings` — жёлтые строки, ввод не блокируют.
- любой другой `type` — компонент ничего не рисует, текст сообщения остаётся.

`choices.segments[]`: `code`, `list` (`included` или `excluded`), `type`, `count`, `state`. `choices.places[]`: `id`, `state` (`ACTIVE` или `INACTIVE`), `defaultBanner` (строка или `null`). Запись без любого из этих полей отбрасывается, остальные строки и черновик остаются. Если `choices` нет или код в него не попал, под полем остаётся только имя и код.

Пример:

```json
"choices": {
  "segments": [
    { "code": "FEMALE", "list": "included", "type": "GENDER", "count": 42000000, "state": "ACTIVE" },
    { "code": "CITY_MSK", "list": "included", "type": "GEO_CITY", "count": 13000000, "state": "ACTIVE" }
  ],
  "places": [
    { "id": 1, "state": "ACTIVE", "defaultBanner": "STORY" }
  ]
}
```

Под сегментом строка `GEO_CITY, 13 000 000, ACTIVE`. Под местом `ACTIVE, баннер по умолчанию STORY`. При `defaultBanner: null` — `ACTIVE, баннера по умолчанию нет`. При `audienceType: ALL_USERS` под аудиторией фиксированная фраза экрана: «Списки включённых и исключённых должны быть пустыми».

Коды `missing`: `name`, `startDate`, `startTime`, `topic`, `audienceType`, `places`, `segments`. Пустые поля показываются как «—». Пустые вес и CTR тоже прочерк, не ноль. Если пусты оба, под весом строка: «Если не задать, при сохранении сервер поставит вес 0 и CTR 15». Если пуст только один, подсказка называет только его. Это подпись экрана: в `draft.weight` и `draft.initialCtr` нули от неё не появляются. Кнопка `вес 10` шлёт этот текст как обычную реплику.

Пока job `PENDING`, поле заблокировано и виден статус «Собираю ответ». Опрос раз в секунду, не дольше 45 попыток. `FAILED` и ошибка сети пишутся в ленту как сообщение ассистента. Если сессия не открылась, шапка пишет «Сессия не создана», а поле остаётся закрытым.

## Мок

Включён, если `VITE_USE_MOCK=true` (так в `.env.development`). Класс `MockChatClient`, сессия `mock-session`. Первый опрос job — `PENDING`, второй — `DONE` или `FAILED`.

- фраза с «вклад» и «москв» — черновик, `readyToSave: false`, `missing: ["startTime"]`. В тексте причина: Москва, `GEO_CITY`, 13 000 000, `ACTIVE`, и место 1 с баннером `STORY`. В `choices` ещё возраст 26–45 и исключение `EXC_UNSUBSCRIBED`
- фраза с «баня» или «аквапарк» и «женщин» или «старше 35» — черновик «Баня и аквапарк»: `FEMALE`, `CITY_MSK`, место `1`, без даты, времени, темы и возраста. Первый ответ называет женский пол (`GENDER`, 42 000 000, `ACTIVE`), Москву и место 1. В `missing` только один текущий код, по порядку `startDate`, `startTime`, `topic`, `segments`. Дата и время без кнопок. Из реплики копируются `YYYY-MM-DD` и `HH:mm`. «С завтра» ставит начало на следующий день, «на 2 недели» и «на две недели» — конец через 14 дней от начала, а если начала нет, начало сегодня. «До конца года» ставит конец `YYYY-12-31`. Дата раньше сегодня и конец раньше начала не записываются. Фраза без даты начало не заполняет. Тема — кнопки `LIFESTYLE` и `EVENT`. Возраст — `AGE_26_45` и `AGE_46_60`. Пока дыры есть, `подтверждаю` не сохраняет и не подменяет черновик вкладом
- «широкий охват» вес не пишет и предлагает 10. Кнопка `вес 10` есть на шаге даты, времени и собранного черновика. На шаге темы и возраста кнопки справочника остаются, в тексте сказано написать `вес 10`. `вес N` записывает вес, `ctr N` записывает CTR. «подтверждаю» согласием на вес не считается. При появлении `savedCampaignId` пустой вес становится 0, пустой CTR — 15, текст говорит, что числа записал сервер. Бриф про вклад свои даты не пересчитывает
- `choices` мок собирает сам по кодам черновика. Факты есть у `FEMALE`, `CITY_MSK`, `AGE_26_45`, `AGE_46_60`, `EXC_UNSUBSCRIBED` и мест 1, 4, 8. Другой код остаётся без второй строки. Если место `INACTIVE`, к ответу добавляется `warnings` «Место показа выключено», `readyToSave` не снимается. Текущие брифы используют место 1, поэтому плашки у них нет
- ровно `подтверждаю` на готовом черновике вклада — время `10:00` и кнопка сохранения; следующее подтверждение ставит `savedCampaignId: 201` и `readyToSave: false`, кнопки «Сохранить» на этом сообщении нет. Ещё одно `подтверждаю` отвечает только «Кампания уже сохранена. Идентификатор 201.» без нового блока черновика. На собранном своём брифе то же сохранение, но поля бани остаются
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
- `frontend/src/mock/fixtures.ts` — черновик вклада, черновик бани, узнавание фраз, коды возраста и темы, факты `choices`, расчёт дат и веса
- `frontend/src/mock/mockClient.ts` — сессия, job из двух опросов, ветки вклада и своего брифа
- `frontend/src/chat/useChat.ts` — сессия, отправка, опрос
- `frontend/src/chat/selectors.ts` — последний черновик и последние быстрые ответы
- `frontend/src/chat/phrases.ts` — `подтверждаю`, `отмена`, логин, две стартовые фразы (`OWN_BRIEF`, `STARTER_BRIEF`)
- `frontend/src/labels/fields.ts` — подписи полей, аудитории, `missing`, строки причины из `choices` и подсказка про вес 0 и CTR 15
- `frontend/src/labels/states.ts` — ярлык того же `state` и отдельно `published`
- `frontend/src/labels/catalog.ts` — имена сегментов, мест, тегов и подпись кнопки `replyLabel`
- `frontend/src/components/layout/AppShell.tsx` — колонки, стартовая ширина черновика 460px и ручка изменения ширины
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
- `frontend/src/components/blocks/CampaignDraftCard.tsx` — полная таблица черновика справа
- `frontend/src/components/blocks/DraftChangeNote.tsx` — в ленте имя, что изменилось, что осталось, «Сохранить»
- `frontend/src/labels/draftRows.ts` — сравнение полей соседних черновиков
- `frontend/src/components/blocks/DraftField.tsx` — строка поля и необязательная вторая строка
- `frontend/src/components/blocks/MissingList.tsx` — «осталось уточнить»
- `frontend/src/components/blocks/QuickReplies.tsx` — подпись кнопки из справочника, отправка исходной строки
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

## Куда идти

- Отправка, опрос, блокировка поля: `frontend/src/chat/useChat.ts`, `frontend/src/components/composer/Composer.tsx`, `frontend/src/components/composer/PendingStatus.tsx`
- Заголовки и роль: `frontend/src/api/headers.ts`, `frontend/src/components/layout/HeaderBar.tsx`, `frontend/src/components/layout/RoleSelect.tsx`
- Форма JSON и новый `type`: `frontend/src/api/types.ts`, `frontend/src/api/guards.ts`, `frontend/src/components/thread/BlockList.tsx`
- HTTP и ошибка сети: `frontend/src/api/httpClient.ts`, `frontend/src/api/errors.ts`
- Фразы мока и свой бриф: `frontend/src/mock/mockClient.ts`, `frontend/src/mock/fixtures.ts`, `frontend/src/chat/phrases.ts`, `frontend/src/components/thread/EmptyThread.tsx`
- Карточка черновика, `choices` и «Сохранить»: `frontend/src/components/blocks/CampaignDraftCard.tsx`, `frontend/src/components/blocks/DraftChangeNote.tsx`, `frontend/src/components/blocks/DraftField.tsx`, `frontend/src/labels/draftRows.ts`, `frontend/src/labels/fields.ts`, `frontend/src/api/types.ts`, `frontend/src/api/guards.ts`, `frontend/src/components/blocks/MissingList.tsx`, `frontend/src/components/draft/DraftRail.tsx`, `frontend/src/components/layout/AppShell.tsx`
- Подписи полей и статусов: `frontend/src/labels/fields.ts`, `frontend/src/labels/states.ts`, `frontend/src/labels/catalog.ts`
- Превью баннера: `frontend/src/components/blocks/CreativePreview.tsx`, `frontend/src/components/blocks/StoryFrame.tsx`, `frontend/src/components/blocks/SmartBannerFrame.tsx`
- Быстрые ответы: `frontend/src/components/blocks/QuickReplies.tsx`, `frontend/src/labels/catalog.ts`, `frontend/src/components/composer/QuickReplyBar.tsx`, `frontend/src/chat/selectors.ts`
- Цвета и страница: `frontend/src/styles/global.css`, `frontend/src/components/layout/AppShell.tsx`
- Сборка экрана: `frontend/src/App.tsx`

## Стыковка с агентом, сервером и LLM

Экран уже рисует причину. Остальным слоям нужно положить те же поля в блок сообщения. Новый `type` не нужен. Отдельное поле «что изменилось» присылать не нужно: лента и подсветка справа сравнивают соседние блоки `campaign_draft`.

**Агент.** После выбора кодов в `EntityResolver` кладёт `choices` в блок `campaign_draft` из снимка каталога. Сегмент: `code`, `list`, `type`, `count`, `state`. Место: `id`, `state`, `defaultBanner`. Имя сегмента и описание места фронт уже показывает сам. Число, тип и состояние он не додумает: нет записи в `choices` — нет второй строки. В текст ответа кладётся та же причина словами справочника, например «Москва, GEO_CITY, 13 000 000, ACTIVE» и «главный экран, место 1, баннер по умолчанию STORY». Модель эти числа не назначает. Место `INACTIVE` добавляет блок `warnings` со строкой «Место показа выключено» и не снимает `readyToSave`. Для `ALL_USERS` оба списка сегментов пустые, и текст ответа прямо это говорит. Для `SEGMENTED` без хотя бы одного `ACTIVE` во включении в `missing` остаётся `segments`.

**LLM.** `choices`, `count`, `type`, `state` и id места не заполняет. В патч по-прежнему можно положить `segmentQuery`, `placeQuery` или `tagQuery`, если строки нет в каталоге. Короткий текст «почему так» собирается только из строк, которые автомат уже передал в вопрос. Иначе модель объяснит сегмент, которого нет.

**Сервер.** Отдельная таблица причин не нужна. `choices` живёт в JSON сообщения сессии рядом с блоком и в таблицу кампании не пишется. При `SAVE_CAMPAIGN` сохраняются `includedSegments`, `excludedSegments` и `placeIds`. Каталог для автомата по-прежнему целиком уходит в `TurnInput` из базы. Job тот же: реплика, автомат, при пустом `commit` — сохранить черновик сессии.

Даты и охват стыкуются так же, без нового `type`.

**Агент.** Относительную дату считает код от `today` в `TurnInput`. «С завтра» — следующий день, «на 2 недели» — конец через 14 дней от начала, если начала нет, начало равно `today`. «До конца года» — `YYYY-12-31`. Фраза без даты оставляет `startDate` пустым. Время берётся только из явного `HH:mm`, иначе в `missing` остаётся `startTime`. Начало не раньше `today`, конец не раньше начала: такие значения в черновик не пишутся, в тексте короткая причина. «Широкий охват» не кладёт вес в патч. В тексте предложение 10, кнопка `quick_replies` шлёт строку `вес 10`, не «подтверждаю». `вес N` и `ctr N` записывают названное число. В черновик 0 и 15 сами не подставляются.

**LLM.** В схеме `intent` даты вида «на 2 недели», «с завтра» и «до конца года» остаются null: их посчитает код. Вес и CTR остаются null, если во фразе нет цифры или согласия на уже названное число. Модель не ставит 0 и 15 за менеджера.

**Сервер.** `today` в `TurnInput` — текущая дата UTC `YYYY-MM-DD`. При `SAVE_CAMPAIGN` null-вес становится 0, null-CTR становится 15. В аудит пишется уже это сохранённое значение, чтобы было видно, что число пришло от сервера. До сохранения в ответе клиенту вес и CTR остаются null, фронт показывает прочерк и подпись. После `SAVE_CAMPAIGN` агент ставит `readyToSave: false`. Следующее «подтверждаю» не становится вторым сохранением и не присылает новый блок `campaign_draft`.
