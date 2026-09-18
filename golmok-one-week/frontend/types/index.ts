/** 백엔드 API 응답 타입. 디자인 변경 시에도 이 타입 이름과 필드는 유지한다. */

export type MenuCategory =
  | "국물요리" | "구이" | "냉면류" | "분식" | "치킨"
  | "일식" | "중식" | "양식" | "보양식" | "기타";

export const MENU_CATEGORIES = [
  "국물요리", "구이", "냉면류", "분식", "치킨", "일식", "중식", "양식", "보양식", "기타",
] as const satisfies readonly MenuCategory[];

export type Confidence = "HIGH" | "MEDIUM" | "LOW";
export type Priority = "HIGH" | "MEDIUM" | "LOW";
export type RecommendationType =
  | "INVENTORY" | "STAFFING" | "MENU" | "DELIVERY" | "MARKETING" | "NOTICE";
export type ConditionType =
  | "RAIN" | "HOT" | "COLD" | "WEEKEND" | "HOLIDAY" | "FESTIVAL" | "COMPETITION" | "PRICE_SPIKE";
export type SourceType = "PAPER" | "PUBLIC_DATA" | "API" | "FESTIVAL" | "INDUSTRY" | "DEMO";

export interface Store {
  id: number;
  name: string;
  category: string | null;
  address: string | null;
  roadAddress: string | null;
  latitude: number | null;
  longitude: number | null;
  city: string | null;
  district: string | null;
  isDemoData: boolean;
}

export interface MenuClassification {
  menuCategory: MenuCategory;
  confidence: Confidence;
  matchedKeywords: string[];
  isDemoData: boolean;
}

export interface WeatherDay {
  date: string;
  dayOfWeek: string;
  condition: string;
  tempMax: number | null;
  tempMin: number | null;
  precipitationProbability: number | null;
  precipitationMm: number | null;
  humidity: number | null;
  isDemoData: boolean;
  sourceId: number | null;
}

export interface CommercialArea {
  district: string | null;
  dong: string | null;
  totalStores: number | null;
  sameCategoryStores: number | null;
  competitionLevel: string | null;
  note: string | null;
  isDemoData: boolean;
  sourceIds: number[];
}

export interface FestivalEvent {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  locationName: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  distanceMeters: number | null;
  impactLevel: string | null;
  impactNote: string | null;
  isDemoData: boolean;
  sourceId: number | null;
  playTime?: string | null;
  fee?: string | null;
  contact?: string | null;
  fetchedAt?: string | null;
}

export interface IngredientPrice {
  item: string;
  unit: string | null;
  price: number | null;
  priceDate: string | null;
  probSpike: number | null;
  alert: boolean;
  vsNormalRatio: number | null;
  isDemoData: boolean;
  sourceId: number | null;
  history?: { date: string; price: number }[];
  comparisonDate?: string | null;
  vsPreviousWeekRatio?: number | null;
  predictionDate?: string | null;
  predictionStale?: boolean;
  priceBasis?: string | null;
}

export interface Recommendation {
  title: string;
  text: string;
  type: RecommendationType;
  priority: Priority;
  confidence: Confidence;
  conditionType: ConditionType;
  basis: string | null;
  date: string | null;
  sourceIds: number[];
}

export interface DailyGuide {
  date: string;
  dayOfWeek: string;
  weatherSummary: string;
  guides: Recommendation[];
}

export interface Source {
  id: number;
  sourceType: SourceType;
  title: string;
  organization: string | null;
  authors: string | null;
  publicationYear: number | null;
  url: string | null;
  description: string | null;
  reliabilityNote: string | null;
}

export interface AnalysisReport {
  reportId: number;
  store: Store;
  mainMenu: string;
  menuCategory: MenuCategory;
  analysisStartDate: string;
  analysisEndDate: string;
  summary: string;
  aiSummary: string | null;
  topActions: Recommendation[];
  weather: WeatherDay[];
  commercialArea: CommercialArea | null;
  festivals: FestivalEvent[];
  ingredientPrices: IngredientPrice[];
  dailyGuides: DailyGuide[];
  sources: Source[];
  isDemoData: boolean;
  demoNotice: string | null;
  disclaimer: string;
}

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
}

/** 화면 상태: initial | loading | success | empty | error */
export type ViewState = "initial" | "loading" | "success" | "empty" | "error";
