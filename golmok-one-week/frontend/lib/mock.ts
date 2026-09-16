import type { AnalysisReport, MenuCategory, MenuClassification, Source, Store, WeatherDay } from "@/types";

/** 백엔드가 없을 때 프론트 단독으로 화면 흐름을 보여주기 위한 데모 데이터. */

const DEMO_STORES: Store[] = [
  demoStore(1, "교촌치킨 두류점", "호프/통닭", "대구광역시 달서구 두류동 620-12", "달서구", 35.8521, 128.55481),
  demoStore(2, "1977(일구칠칠)곱창이", "식육(숯불구이)", "대구광역시 남구 대명동 817-1", "남구", 35.838053, 128.575921),
  demoStore(3, "교동생선구이비빔밥", "분식", "대구광역시 중구 교동 0068-0002 지상1층", "중구", 35.873247, 128.597138),
  demoStore(4, "대동강", "냉면집", "대구광역시 남구 봉덕동 948-13", "남구", 35.843911, 128.603888),
  demoStore(5, "마차이짬뽕", "중국식", "대구광역시 중구 동성로3가 0035-0005 지상1층", "중구", 35.867541, 128.593355),
];

function demoStore(
  id: number, name: string, category: string, address: string, district: string, lat: number, lon: number,
): Store {
  return {
    id, name, category, address, roadAddress: address, latitude: lat, longitude: lon,
    city: "대구광역시", district, isDemoData: true,
  };
}

/** id 로 데모 가게 하나를 찾는다. 없으면 첫 번째 가게를 돌려준다. */
export function mockStore(storeId: number): Store {
  return DEMO_STORES.find((s) => s.id === storeId) ?? DEMO_STORES[0];
}

export function mockStores(keyword: string): Store[] {
  const k = keyword.trim();
  if (!k) return [];
  return DEMO_STORES.filter((s) => s.name.includes(k) || (s.address ?? "").includes(k));
}

const KEYWORDS: Record<string, MenuCategory> = {
  국밥: "국물요리", 찌개: "국물요리", 탕: "국물요리",
  닭똥집: "구이", 막창: "구이", 곱창: "구이", 삼겹살: "구이",
  냉면: "냉면류", 밀면: "냉면류",
  떡볶이: "분식", 김밥: "분식", 납작만두: "분식", 만두: "분식", 튀김: "분식",
  치킨: "치킨", 닭강정: "치킨",
  초밥: "일식", 돈까스: "일식",
  짜장: "중식", 짬뽕: "중식",
  파스타: "양식", 피자: "양식",
  삼계탕: "보양식", 장어: "보양식",
};

export function mockClassify(menuName: string): MenuClassification {
  const hit = Object.keys(KEYWORDS).find((k) => menuName.replace(/\s/g, "").includes(k));
  if (!hit) return { menuCategory: "기타", confidence: "LOW", matchedKeywords: [], isDemoData: true };
  return { menuCategory: KEYWORDS[hit], confidence: "HIGH", matchedKeywords: [hit], isDemoData: true };
}

const INGREDIENT_PRICES: Record<string, { unit: string; price: number; probSpike: number; alert: boolean; vsNormalRatio: number }> = {
  계란: { unit: "30구(1판)", price: 7247, probSpike: 0.03, alert: false, vsNormalRatio: 0.16 },
  깐마늘: { unit: "1kg", price: 9280, probSpike: 0.01, alert: false, vsNormalRatio: -0.19 },
  닭: { unit: "1kg", price: 4269, probSpike: 0.01, alert: false, vsNormalRatio: -0.24 },
  대파: { unit: "1kg", price: 2573, probSpike: 0.05, alert: false, vsNormalRatio: -0.21 },
  무: { unit: "1개", price: 2470, probSpike: 0.02, alert: false, vsNormalRatio: -0.07 },
  배추: { unit: "1포기", price: 5363, probSpike: 0.04, alert: false, vsNormalRatio: -0.22 },
  삼겹살: { unit: "100g", price: 2918, probSpike: 0.0, alert: false, vsNormalRatio: 0.09 },
  양파: { unit: "1kg", price: 1390, probSpike: 0.05, alert: false, vsNormalRatio: -0.38 },
};

const MENU_INGREDIENTS: Partial<Record<MenuCategory, string[]>> = {
  치킨: ["닭"],
  구이: ["삼겹살"],
  국물요리: ["대파", "무", "배추"],
  분식: ["대파", "양파", "계란"],
  중식: ["양파", "대파", "깐마늘"],
  보양식: ["닭", "깐마늘"],
};

function demoIngredientPrices(category: MenuCategory, priceDate: string): AnalysisReport["ingredientPrices"] {
  return (MENU_INGREDIENTS[category] ?? []).map((item) => ({
    item, priceDate, isDemoData: true, sourceId: 14, ...INGREDIENT_PRICES[item],
  }));
}

export function mockSources(): Source[] {
  return [
    {
      id: 1, sourceType: "PUBLIC_DATA", title: "행정안전부 식품_일반음식점 인허가 정보",
      organization: "행정안전부", authors: null, publicationYear: null,
      url: "https://www.data.go.kr/data/15045016/fileData.do",
      description: "대구 지역 일반음식점 인허가 현황(상호명, 업태구분, 영업상태, 주소, 좌표 등)을 제공하는 공공데이터",
      reliabilityNote: "영업 중인 상가업소만 사용하나 데이터 갱신 시점에 따라 실제 현황과 차이가 있을 수 있음",
    },
    {
      id: 6, sourceType: "API", title: "기상청 기상자료개방포털", organization: "기상청",
      authors: null, publicationYear: null, url: "https://data.kma.go.kr/",
      description: "기온, 강수, 풍속 등 기상 데이터 제공", reliabilityNote: "예보는 변경될 수 있음",
    },
  ];
}

function demoWeather(): WeatherDay[] {
  const base = [
    { condition: "맑음", tempMax: 27, tempMin: 18, pop: 10, mm: 0, reh: 55 },
    { condition: "흐림", tempMax: 25, tempMin: 19, pop: 30, mm: 0, reh: 70 },
    { condition: "비", tempMax: 22, tempMin: 19, pop: 80, mm: 12.5, reh: 90 },
    { condition: "비", tempMax: 23, tempMin: 19.5, pop: 60, mm: 5, reh: 85 },
    { condition: "맑음", tempMax: 31, tempMin: 21, pop: 10, mm: 0, reh: 50 },
    { condition: "맑음", tempMax: 33.5, tempMin: 23, pop: 0, mm: 0, reh: 45 },
    { condition: "흐림", tempMax: 29, tempMin: 20, pop: 20, mm: 0, reh: 65 },
  ];
  const dow = ["월", "화", "수", "목", "금", "토", "일"];
  return base.map((b, i) => {
    const d = new Date();
    d.setDate(d.getDate() + i);
    return {
      date: d.toISOString().slice(0, 10),
      dayOfWeek: dow[(d.getDay() + 6) % 7],
      condition: b.condition, tempMax: b.tempMax, tempMin: b.tempMin,
      precipitationProbability: b.pop, precipitationMm: b.mm, humidity: b.reh,
      isDemoData: true, sourceId: 6,
    };
  });
}

/** 방금 만든 데모 리포트. 같은 화면 안에서 리포트를 다시 조회할 때 그대로 재사용한다. */
let lastReport: AnalysisReport | null = null;

/** reportId 로 데모 리포트를 조회한다. 직전에 만든 리포트가 있으면 그것을 쓴다. */
export function mockReportById(reportId: number): AnalysisReport {
  return lastReport?.reportId === reportId ? lastReport : mockReport(1, "닭똥집", "구이", reportId);
}

export function mockReport(
  storeId: number, mainMenu: string, menuCategory: MenuCategory, reportId = 1,
): AnalysisReport {
  const store = DEMO_STORES.find((s) => s.id === storeId) ?? DEMO_STORES[0];
  const weather = demoWeather();
  const topActions: AnalysisReport["topActions"] = [
    {
      title: "비 예보 · 포장·배달 점검", type: "DELIVERY", priority: "HIGH", confidence: "HIGH", conditionType: "RAIN",
      text: "비 예보일에는 홀 방문보다 포장·배달 주문이 늘어나는 경향이 보고됩니다. 포장·배달 가능 여부와 포장 용기 재고를 점검하는 것을 권장합니다.",
      basis: `${weather[2].date}(${weather[2].dayOfWeek}) 강수확률 80%`, date: weather[2].date, sourceIds: [4],
    },
    {
      title: "주말 · 인력·운영 점검", type: "STAFFING", priority: "MEDIUM", confidence: "HIGH", conditionType: "WEEKEND",
      text: "주말은 피크 시간대 인력과 재료를 사전에 점검하는 것을 권장합니다.",
      basis: "주말 수요 변화 가능성", date: weather[5].date, sourceIds: [4],
    },
    {
      title: "경쟁 상권 · 노출·안내 점검", type: "MARKETING", priority: "MEDIUM", confidence: "MEDIUM", conditionType: "COMPETITION",
      text: "반경 500m 내 유사 업종이 많습니다. 대표 메뉴, 포장 품질, 대기 시간, 응대 속도의 차별화 점검을 권장합니다. 다만 음식점이 많은 지역일수록 매출도 함께 증가한다는 분석이 있어, 경쟁점 수만으로 상권을 낮게 평가하지는 않습니다.",
      basis: "반경 500m 내 유사 업종 3곳", date: null, sourceIds: [1],
    },
  ];
  lastReport = {
    reportId,
    store,
    mainMenu,
    menuCategory,
    analysisStartDate: weather[0].date,
    analysisEndDate: weather[6].date,
    summary: "이번 주는 비 예보 2일, 30℃ 이상 2일 조건입니다. 아래 점검 항목은 운영 참고용이며 수요 변화 가능성에 대비한 준비 권장 사항입니다.",
    topActions,
    weather,
    commercialArea: {
      district: store.district, dong: "데모 동", totalStores: 8, sameCategoryStores: 3,
      competitionLevel: "높음", note: "반경 500m 기준 음식점 8곳, 유사 업종 3곳 (데모 데이터)",
      isDemoData: true, sourceIds: [1],
    },
    ingredientPrices: demoIngredientPrices(menuCategory, weather[0].date),
    festivals: [
      {
        id: 1, name: "대구치맥페스티벌 (데모)", startDate: weather[2].date, endDate: weather[5].date,
        locationName: "두류공원", address: "대구광역시 달서구 공원순환로 36", latitude: 35.8509, longitude: 128.5588,
        distanceMeters: 300, impactLevel: "직접 영향 가능",
        impactNote: "행사장과 가까워 방문객 유입 가능성이 있으며, 교통·주차 혼잡도 함께 고려가 필요합니다.",
        isDemoData: true, sourceId: 5,
      },
    ],
    dailyGuides: weather.map((w) => ({
      date: w.date, dayOfWeek: w.dayOfWeek,
      weatherSummary: `${w.condition} · 최고 ${w.tempMax}℃ / 최저 ${w.tempMin}℃ · 강수확률 ${w.precipitationProbability}%`,
      guides: (w.precipitationProbability ?? 0) >= 60 ? [topActions[0]] : [],
    })),
    sources: mockSources(),
    isDemoData: true,
    demoNotice: "백엔드에 연결하지 못해 프론트 데모 데이터로 표시하고 있습니다.",
    disclaimer: "본 결과는 공공데이터 및 연구자료를 기반으로 한 운영 참고용 제안이며, 실제 매출을 보장하지 않습니다.",
  };
  return lastReport;
}
