package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.BannerTypeRef
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.ChannelRef
import ru.zhma.campaign.ai.model.PlaceRef
import ru.zhma.campaign.ai.model.SegmentRef
import ru.zhma.campaign.ai.model.TagRef
import ru.zhma.campaign.ai.model.TopicRef

/** Справочник как в сиде сервера (без лишних интересов). */
object TestCatalog {
    fun full() =
        CatalogSnapshot(
            topics =
                listOf(
                    TopicRef("EVENT", "Событие"),
                    TopicRef("LIFESTYLE", "Lifestyle"),
                    TopicRef("SALES", "Продажи"),
                    TopicRef("COURSES", "Курсы"),
                    TopicRef("OTHER", "OTHER"),
                ),
            tags = listOf(TagRef(1, "Банковские продукты"), TagRef(2, "Промо"), TagRef(5, "Вклад")),
            segments =
                listOf(
                    SegmentRef("CITY_MSK", "Москва", "GEO_CITY", "ACTIVE", 13_000_000),
                    SegmentRef("CITY_SPB", "Санкт-Петербург", "GEO_CITY", "ACTIVE", 5_600_000),
                    SegmentRef("CITY_SLD", "Сочи", "GEO_CITY", "ACTIVE", 400_000),
                    SegmentRef("REGION_MOS_OBL", "Московская область", "GEO_REGION", "ACTIVE", 8_500_000),
                    SegmentRef("AGE_18_25", "Возраст 18–25", "AGE", "ACTIVE", 9_000_000),
                    SegmentRef("AGE_26_45", "Возраст 26–45", "AGE", "ACTIVE", 24_000_000),
                    SegmentRef("AGE_46_60", "Возраст 46–60", "AGE", "ACTIVE", 18_000_000),
                    SegmentRef("AGE_60_PLUS", "Возраст 60+", "AGE", "ACTIVE", 9_500_000),
                    SegmentRef("MALE", "Мужской пол", "GENDER", "ACTIVE", 34_000_000),
                    SegmentRef("FEMALE", "Женский пол", "GENDER", "ACTIVE", 42_000_000),
                    SegmentRef("INTRS_TRAVEL", "Путешествия", "INTEREST", "ACTIVE", 6_800_000),
                    SegmentRef("SEG_DRAFT_WINTER", "Черновик. Зимняя кампания", "BASIC", "NEW", 0),
                ),
            places =
                listOf(
                    PlaceRef(1, "mainScreenStories", "Промо-блок на главном экране", "STORY", "ACTIVE"),
                    PlaceRef(2, "mainScreenSmartBanner", "Смарт-баннер на главном", "SMART_BANNER", "ACTIVE"),
                    PlaceRef(3, "catalogPromo", "Промо-баннер в каталоге", "SMART_BANNER", "ACTIVE"),
                    PlaceRef(4, "oldStoriesSlot", "Архивный сторис-слот", "STORY", "INACTIVE"),
                ),
            bannerTypes =
                listOf(
                    BannerTypeRef("STORY", 100, 500, listOf("iOS", "ANDROID")),
                    BannerTypeRef("SMART_BANNER", 250, 1000, listOf("iOS", "ANDROID", "WEB")),
                ),
            channels = listOf(ChannelRef("iOS", "iOS"), ChannelRef("ANDROID", "Android"), ChannelRef("WEB", "Web")),
        )
}
