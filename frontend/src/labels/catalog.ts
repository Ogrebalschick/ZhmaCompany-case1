const TOPICS: Record<string, string> = {
  LIFESTYLE: "Образ жизни",
  EVENT: "Событие",
  SALES: "Продажи",
};

const TAGS: Record<number, string> = {
  1: "Банковские продукты",
  2: "Промо",
  3: "Скидки",
  4: "Карты",
  5: "Вклад",
  6: "Онбординг",
};

const SEGMENTS: Record<string, string> = {
  CITY_MSK: "Москва",
  CITY_SPB: "Санкт-Петербург",
  CITY_NVS: "Новосибирск",
  CITY_KZN: "Казань",
  CITY_EKB: "Екатеринбург",
  CITY_SLD: "Сочи",
  REGION_MOS_OBL: "Московская область",
  REGION_LEN_OBL: "Ленинградская область",
  REGION_KRD_KRAI: "Краснодарский край",
  REGION_SVD_OBL: "Свердловская область",
  AGE_18_25: "Возраст 18–25",
  AGE_26_45: "Возраст 26–45",
  AGE_46_60: "Возраст 46–60",
  AGE_60_PLUS: "Возраст 60+",
  MALE: "Мужской пол",
  FEMALE: "Женский пол",
  INTRS_FOOD: "Еда",
  INTRS_TRAVEL: "Путешествия",
  INTRS_SPORT: "Спорт",
  INTRS_CARS: "Автомобили",
  INTRS_HOME_REPAIR: "Дом и ремонт",
  INTRS_CHILDREN: "Дети",
  INTRS_CINEMA: "Кино",
  PRODUCT_CC: "Кредитная карта",
  PRODUCT_DC: "Дебетовая карта",
  PRODUCT_DEP: "Вклад",
  PRODUCT_PREMIUM: "Премиальная карта",
  PRODUCT_MORTGAGE: "Ипотека",
  PRODUCT_AUTO_LOAN: "Автокредит",
  PRODUCT_INSURANCE: "Страхование",
  PRODUCT_SUBSCRIPTION: "Подписки",
  CHANNEL_VIP: "Канал VIP",
  CHANNEL_MASS: "Массовый канал",
  CHANNEL_MID: "Средний канал",
  CHANNEL_NEW_USER: "Новые пользователи приложения",
  EXC_MORATORIUM: "Исключение. Мораторий",
  EXC_UNSUBSCRIBED: "Исключение. Отписались от рекламы",
  EXC_OVERCOMMUNICATED: "Исключение. Перекоммуницированы",
  EXC_EMPLOYEES: "Исключение. Сотрудники банка",
  SEG_DRAFT_WINTER: "Черновик. Зимняя кампания",
};

const PLACES: Record<number, { code: string; description: string; inactive: boolean }> = {
  1: { code: "mainScreenStories", description: "Промо-блок на главном экране", inactive: false },
  2: { code: "mainScreenSmartBanner", description: "Смарт-баннер на главном", inactive: false },
  3: { code: "catalogPromo", description: "Промо-баннер в каталоге", inactive: false },
  4: { code: "oldStoriesSlot", description: "Архивный сторис-слот", inactive: true },
  5: { code: "paymentsScreenBanner", description: "Баннер на экране платежей", inactive: false },
  6: { code: "historyScreenBanner", description: "Баннер на экране истории операций", inactive: false },
  7: { code: "depositScreenPromo", description: "Промо на экране вкладов", inactive: false },
  8: { code: "oldCatalogSlot", description: "Слот каталога до редизайна", inactive: true },
};

export function topicLabel(code: string): string {
  const name = TOPICS[code];
  return name ? `${name} (${code})` : code;
}

export function replyLabel(option: string): string {
  if (SEGMENTS[option]) return segmentLabel(option);
  if (TOPICS[option]) return topicLabel(option);
  if (/^\d+$/.test(option)) {
    const placeId = Number(option);
    if (PLACES[placeId]) return placeLabel(placeId);
  }
  return option;
}

export function segmentLabel(code: string): string {
  const name = SEGMENTS[code];
  return name ? `${name} (${code})` : code;
}

export function placeLabel(id: number): string {
  const place = PLACES[id];
  if (!place) return String(id);
  const state = place.inactive ? ", выключено" : "";
  return `${place.description} (${place.code}${state})`;
}

export function tagLabel(id: number): string {
  return TAGS[id] ? `${TAGS[id]} (${id})` : String(id);
}

export function formatSegments(codes: string[] | undefined): string {
  if (!codes || codes.length === 0) return "—";
  return codes.map(segmentLabel).join(", ");
}

export function formatPlaces(ids: number[] | undefined): string {
  if (!ids || ids.length === 0) return "—";
  return ids.map(placeLabel).join(", ");
}

export function formatTags(ids: number[] | undefined): string {
  if (!ids || ids.length === 0) return "—";
  return ids.map(tagLabel).join(", ");
}
