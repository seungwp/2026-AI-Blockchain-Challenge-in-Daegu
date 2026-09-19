"use client";

import { useSyncExternalStore } from "react";
import { z } from "zod";
import { MENU_CATEGORIES } from "@/types";
import type { Store } from "@/types";

const storeSchema = z.object({
  id: z.number().int().positive(), name: z.string(), category: z.string().nullable(),
  address: z.string().nullable(), roadAddress: z.string().nullable(),
  latitude: z.number().nullable(), longitude: z.number().nullable(),
  city: z.string().nullable(), district: z.string().nullable(), isDemoData: z.boolean(),
});
const schema = z.object({
  keyword: z.string(), store: storeSchema.nullable(), mainMenu: z.string(),
  menuCategory: z.enum(MENU_CATEGORIES), classifiedMenu: z.string(),
  classification: z.object({
    menuCategory: z.enum(MENU_CATEGORIES), confidence: z.enum(["HIGH", "MEDIUM", "LOW"]),
    matchedKeywords: z.array(z.string()), isDemoData: z.boolean(),
  }).nullable(),
});
type Draft = z.infer<typeof schema>;
const empty: Draft = { keyword: "", store: null, mainMenu: "", menuCategory: "기타", classifiedMenu: "", classification: null };
const key = "golmok:onboarding:v1";
let snapshot = empty;
let initialized = false;
const listeners = new Set<() => void>();

export function readDraft(): Draft {
  if (typeof window === "undefined") return empty;
  if (!initialized) {
    initialized = true;
    try {
      const parsed = schema.safeParse(JSON.parse(sessionStorage.getItem(key) ?? "null"));
      if (parsed.success) snapshot = parsed.data;
    } catch { /* 저장소를 사용할 수 없어도 현재 탭의 메모리로 진행한다. */ }
  }
  return snapshot;
}
export function saveDraft(patch: Partial<Draft>) {
  snapshot = { ...readDraft(), ...patch };
  try { sessionStorage.setItem(key, JSON.stringify(snapshot)); } catch { /* 메모리 폴백 */ }
  listeners.forEach((listener) => listener());
}
export function selectStore(store: Store) {
  const previous = readDraft();
  saveDraft({ store, ...(previous.store?.id === store.id ? {} : {
    mainMenu: "", menuCategory: "기타" as const, classifiedMenu: "", classification: null,
  }) });
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
export function useOnboardingDraft() {
  return useSyncExternalStore(subscribe, readDraft, () => empty);
}
