package ru.zhma.campaign.ai.prompt

object Prompts {
    fun intent(
        today: String,
        catalog: String,
        draftJson: String,
    ): String =
        """
Ты — разбор намерения пользователя для кампании.

Сегодня: $today

Текущий черновик:
$draftJson

Каталог:
$catalog


Верни ТОЛЬКО JSON без пояснений, в строгой схеме:
{"intent":"CAMPAIGN_EDIT|CONFIRM|CANCEL|GENERATE_CREATIVE|LIST_CAMPAIGNS|OTHER","patch":{"name":null,"marketingName":null,"topic":null,"tagIds":null,"keywords":null,"audienceType":null,"includedSegments":null,"excludedSegments":null,"segmentQuery":null,"placeIds":null,"placeQuery":null,"tagQuery":null,"startDate":null,"startTime":null,"endDate":null,"endTime":null,"weight":null,"initialCtr":null,"creativeBrief":null},"confirm":null}

Правила:
- Заполняй patch лишь теми полями, что явно сказаны в реплике. Ничего не выдумывай.
- topic, коды сегментов (includedSegments, excludedSegments) и id мест (placeIds) бери только из каталога выше. Если точной записи нет — оставь null и запиши текст в segmentQuery / placeQuery / tagQuery.
- Даты вида «на 2 недели», «до конца года» не рассчитывай — оставь null, автомат посчитает.
- Не выдумывай weight, initialCtr, startTime, name, если их явно не назвали.
- keywords — одна строка, не массив. Несколько слов пиши через запятую.
- confirm: true только если реплика ровно «подтверждаю», «сохрани» или «да».
        """.trimIndent()

    fun askMissing(
        missing: String,
        options: String,
    ): String =
        """
Спроси пользователя про недостающие поля кампании.

Недостающие поля: $missing
Варианты: $options


Правила:
- 1–2 деловых предложения на русском.
- Спроси только про перечисленные поля.
- Если варианты переданы — перечисли их.
- Без эмодзи, без новых вопросов.
        """.trimIndent()

    fun bannerCopy(
        product: String,
        audience: String,
        placement: String,
        n: Int,
    ): String =
        """
Сгенерируй рекламный копирайт.

Продукт: $product
Аудитория: $audience
Место размещения: $placement
Количество вариантов: $n

Верни ТОЛЬКО JSON-массив из $n объектов со схемой:
[{"headline":"...","body":"...","cta":"...","image_prompt_en":"..."}]

Лимиты:
- headline ≤ 40 символов
- body ≤ 90 символов
- cta ≤ 20 символов

Запрещено:
- гарантия одобрения («гарантированно», «100%»)
- «лучший на рынке», «топ»
- проценты и суммы, которых нет во входе
- ЗАГЛАВНЫЕ БУКВЫ и восклицательные знаки
        """.trimIndent()
}
