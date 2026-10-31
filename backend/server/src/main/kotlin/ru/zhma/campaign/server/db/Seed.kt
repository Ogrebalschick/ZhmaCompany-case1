package ru.zhma.campaign.server.db

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

object Seed {
    fun seedIfEmpty() {
        if (TopicsTable.selectAll().count() > 0L) return
        topics.forEach { (value, label) ->
            TopicsTable.insert {
                it[TopicsTable.value] = value
                it[TopicsTable.label] = label
            }
        }
        tags.forEach { (id, name) ->
            TagsTable.insert {
                it[TagsTable.id] = id
                it[TagsTable.name] = name
            }
        }
        segments.forEach { segment ->
            SegmentsTable.insert {
                it[code] = segment.code
                it[name] = segment.name
                it[type] = segment.type
                it[state] = segment.state
                it[count] = segment.count
            }
        }
        places.forEach { place ->
            PlacesTable.insert {
                it[id] = place.id
                it[code] = place.code
                it[description] = place.description
                it[defaultBanner] = place.defaultBanner
                it[state] = place.state
            }
        }
        bannerTypes.forEach { banner ->
            BannerTypesTable.insert {
                it[id] = banner.id
                it[code] = banner.code
                it[description] = banner.description
                it[previewForm] = banner.previewForm
                it[titleMax] = banner.titleMax
                it[textMax] = banner.textMax
                it[channels] = banner.channels
            }
        }
        channels.forEach { (id, code, description) ->
            ChannelsTable.insert {
                it[ChannelsTable.id] = id
                it[ChannelsTable.code] = code
                it[ChannelsTable.description] = description
            }
        }
    }
}

private data class Seg(val code: String, val name: String, val type: String, val state: String, val count: Long)

private data class PlaceSeed(
    val id: Long,
    val code: String,
    val description: String,
    val defaultBanner: String?,
    val state: String,
)

private data class BannerSeed(
    val id: Long,
    val code: String,
    val description: String,
    val previewForm: String,
    val titleMax: Int,
    val textMax: Int,
    val channels: String,
)

private val topics = listOf(
    "EXTRA" to "EXTRA",
    "EVENT" to "Событие",
    "RECOMMENDATION" to "Рекомендации",
    "SALES" to "Продажи",
    "FINANCIAL_TIPS" to "FINANCIAL_TIPS",
    "LIFESTYLE" to "Lifestyle",
    "RETARGET" to "RETARGET",
    "ADVICES" to "Советы",
    "HINTS" to "HINTS",
    "COURSES" to "Курсы",
    "OTHER" to "OTHER",
)

private val tags = listOf(
    1L to "Банковские продукты",
    2L to "Промо",
    3L to "Скидки",
    4L to "Карты",
    5L to "Вклад",
    6L to "Онбординг",
)

private val segments = listOf(
    Seg("CITY_MSK", "Москва", "GEO_CITY", "ACTIVE", 13_000_000),
    Seg("CITY_SPB", "Санкт-Петербург", "GEO_CITY", "ACTIVE", 5_600_000),
    Seg("CITY_NVS", "Новосибирск", "GEO_CITY", "ACTIVE", 1_200_000),
    Seg("CITY_KZN", "Казань", "GEO_CITY", "ACTIVE", 900_000),
    Seg("CITY_EKB", "Екатеринбург", "GEO_CITY", "ACTIVE", 1_100_000),
    Seg("CITY_SLD", "Сочи", "GEO_CITY", "ACTIVE", 400_000),
    Seg("REGION_MOS_OBL", "Московская область", "GEO_REGION", "ACTIVE", 8_500_000),
    Seg("REGION_LEN_OBL", "Ленинградская область", "GEO_REGION", "ACTIVE", 3_200_000),
    Seg("REGION_KRD_KRAI", "Краснодарский край", "GEO_REGION", "ACTIVE", 4_200_000),
    Seg("REGION_SVD_OBL", "Свердловская область", "GEO_REGION", "ACTIVE", 3_100_000),
    Seg("AGE_18_25", "Возраст 18–25", "AGE", "ACTIVE", 9_000_000),
    Seg("AGE_26_45", "Возраст 26–45", "AGE", "ACTIVE", 24_000_000),
    Seg("AGE_46_60", "Возраст 46–60", "AGE", "ACTIVE", 18_000_000),
    Seg("AGE_60_PLUS", "Возраст 60+", "AGE", "ACTIVE", 9_500_000),
    Seg("MALE", "Мужской пол", "GENDER", "ACTIVE", 34_000_000),
    Seg("FEMALE", "Женский пол", "GENDER", "ACTIVE", 42_000_000),
    Seg("INTRS_FOOD", "Еда", "INTEREST", "ACTIVE", 4_210_000),
    Seg("INTRS_TRAVEL", "Путешествия", "INTEREST", "ACTIVE", 6_800_000),
    Seg("INTRS_SPORT", "Спорт", "INTEREST", "ACTIVE", 5_300_000),
    Seg("INTRS_CARS", "Автомобили", "INTEREST", "ACTIVE", 7_400_000),
    Seg("INTRS_HOME_REPAIR", "Дом и ремонт", "INTEREST", "ACTIVE", 3_900_000),
    Seg("INTRS_CHILDREN", "Дети", "INTEREST", "ACTIVE", 6_100_000),
    Seg("INTRS_CINEMA", "Кино", "INTEREST", "ACTIVE", 8_700_000),
    Seg("PRODUCT_CC", "Кредитная карта", "PRODUCT", "ACTIVE", 5_200_000),
    Seg("PRODUCT_DC", "Дебетовая карта", "PRODUCT", "ACTIVE", 32_000_000),
    Seg("PRODUCT_DEP", "Вклад", "PRODUCT", "ACTIVE", 8_100_000),
    Seg("PRODUCT_PREMIUM", "Премиальная карта", "PRODUCT", "ACTIVE", 1_250_000),
    Seg("PRODUCT_MORTGAGE", "Ипотека", "PRODUCT", "ACTIVE", 2_100_000),
    Seg("PRODUCT_AUTO_LOAN", "Автокредит", "PRODUCT", "ACTIVE", 890_000),
    Seg("PRODUCT_INSURANCE", "Страхование", "PRODUCT", "ACTIVE", 4_600_000),
    Seg("PRODUCT_SUBSCRIPTION", "Подписки", "PRODUCT", "ACTIVE", 9_300_000),
    Seg("CHANNEL_VIP", "Канал VIP", "CHANNEL", "ACTIVE", 350_000),
    Seg("CHANNEL_MASS", "Массовый канал", "CHANNEL", "ACTIVE", 50_000_000),
    Seg("CHANNEL_MID", "Средний канал", "CHANNEL", "ACTIVE", 12_500_000),
    Seg("CHANNEL_NEW_USER", "Новые пользователи приложения", "CHANNEL", "ACTIVE", 2_900_000),
    Seg("EXC_MORATORIUM", "Исключение. Мораторий", "BASIC", "ACTIVE", 2_000_000),
    Seg("EXC_UNSUBSCRIBED", "Исключение. Отписались от рекламы", "BASIC", "ACTIVE", 15_000_000),
    Seg("EXC_OVERCOMMUNICATED", "Исключение. Перекоммуницированы", "BASIC", "ACTIVE", 7_600_000),
    Seg("EXC_EMPLOYEES", "Исключение. Сотрудники банка", "BASIC", "ACTIVE", 250_000),
    Seg("SEG_DRAFT_WINTER", "Черновик. Зимняя кампания", "BASIC", "NEW", 0),
)

private val places = listOf(
    PlaceSeed(1, "mainScreenStories", "Промо-блок на главном экране", "STORY", "ACTIVE"),
    PlaceSeed(2, "mainScreenSmartBanner", "Смарт-баннер на главном", "SMART_BANNER", "ACTIVE"),
    PlaceSeed(3, "catalogPromo", "Промо-баннер в каталоге", "SMART_BANNER", "ACTIVE"),
    PlaceSeed(4, "oldStoriesSlot", "Архивный сторис-слот", "STORY", "INACTIVE"),
    PlaceSeed(5, "paymentsScreenBanner", "Баннер на экране платежей", "SMART_BANNER", "ACTIVE"),
    PlaceSeed(6, "historyScreenBanner", "Баннер на экране истории операций", "SMART_BANNER", "ACTIVE"),
    PlaceSeed(7, "depositScreenPromo", "Промо на экране вкладов", "SMART_BANNER", "ACTIVE"),
    PlaceSeed(8, "oldCatalogSlot", "Слот каталога до редизайна", "SMART_BANNER", "INACTIVE"),
)

private val bannerTypes = listOf(
    BannerSeed(1, "STORY", "История", "MOBILE_STORY", 100, 500, "iOS,ANDROID"),
    BannerSeed(2, "SMART_BANNER", "Смарт баннер", "MOBILE_BANNER", 250, 1000, "iOS,ANDROID,WEB"),
)

private val channels = listOf(
    Triple(1L, "iOS", "iOS"),
    Triple(2L, "ANDROID", "Android"),
    Triple(3L, "WEB", "Web"),
)
