import axios from "axios";
import type { AnalysisReport, MenuCategory, MenuClassification, Store } from "@/types";
import { mockClassify, mockReport, mockReportById, mockStore, mockStores } from "./mock";

/** 백엔드 주소. .env.local 의 NEXT_PUBLIC_API_BASE_URL 로 교체한다. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const client = axios.create({ baseURL: API_BASE_URL, timeout: 8000 });

/** 백엔드가 꺼져 있어도 데모 데이터로 전체 흐름이 돌아가게 한다. */
async function withFallback<T>(request: () => Promise<T>, fallback: () => T): Promise<T> {
  try {
    return await request();
  } catch (error) {
    console.warn("[golmok] API 호출 실패 → 데모 데이터로 대체합니다.", error);
    return fallback();
  }
}

export async function searchStores(keyword: string, city = "대구광역시"): Promise<Store[]> {
  return withFallback(
    async () => (await client.get<Store[]>("/api/stores/search", { params: { keyword, city } })).data,
    () => mockStores(keyword),
  );
}

export async function getStore(storeId: number): Promise<Store> {
  return withFallback(
    async () => (await client.get<Store>(`/api/stores/${storeId}`)).data,
    () => mockStore(storeId),
  );
}

export async function createStoreFromAddress(address: string, city = "대구광역시"): Promise<Store> {
  return withFallback(
    async () => (await client.post<Store>("/api/stores/manual", { address, city })).data,
    () => ({ ...mockStore(0), id: 9001, name: `${address} (직접 입력)`, address, roadAddress: address }),
  );
}

export async function classifyMenu(menuName: string, storeCategory?: string): Promise<MenuClassification> {
  return withFallback(
    async () => (await client.post<MenuClassification>("/api/menu/classify", { menuName, storeCategory })).data,
    () => mockClassify(menuName),
  );
}

export async function createReport(
  storeId: number,
  mainMenu: string,
  menuCategory: MenuCategory,
): Promise<AnalysisReport> {
  return withFallback(
    async () => (await client.post<AnalysisReport>("/api/reports", { storeId, mainMenu, menuCategory })).data,
    () => mockReport(storeId, mainMenu, menuCategory),
  );
}

export async function getReport(reportId: number): Promise<AnalysisReport> {
  return withFallback(
    async () => (await client.get<AnalysisReport>(`/api/reports/${reportId}`)).data,
    () => mockReportById(reportId),
  );
}
