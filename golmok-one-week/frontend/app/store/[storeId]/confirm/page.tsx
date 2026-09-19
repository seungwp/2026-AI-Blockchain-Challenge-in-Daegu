"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import OnboardingShell, { Icon } from "@/components/onboarding/OnboardingShell";
import styles from "@/components/onboarding/onboarding.module.css";
import { classifyMenu, getStore } from "@/lib/api";
import { readDraft, saveDraft, selectStore, useOnboardingDraft } from "@/lib/onboarding";
import { MENU_CATEGORIES } from "@/types";
import type { MenuCategory } from "@/types";

const schema = z.object({ mainMenu: z.string().trim().min(1, "대표 메뉴를 입력해주세요.") });
const menuSuggestions: Record<MenuCategory, readonly string[]> = {
  "국물요리": ["돼지국밥", "김치찌개", "순두부찌개", "설렁탕", "된장찌개"],
  "구이": ["삼겹살", "갈비", "소불고기", "닭갈비", "오리구이"],
  "냉면류": ["물냉면", "비빔냉면", "회냉면", "밀면", "갈비냉면"],
  "분식": ["떡볶이", "김밥", "라면", "순대", "튀김"],
  "치킨": ["후라이드치킨", "양념치킨", "간장치킨", "닭강정", "치킨텐더"],
  "일식": ["초밥", "사시미", "돈카츠", "우동", "라멘"],
  "중식": ["짜장면", "짬뽕", "탕수육", "마파두부", "볶음밥"],
  "양식": ["파스타", "피자", "스테이크", "리조또", "샐러드"],
  "보양식": ["삼계탕", "추어탕", "장어구이", "전복죽", "오리백숙"],
  "기타": [],
};

type StoreMenuSuggestion = { category: MenuCategory; menus: readonly string[] };

/** 업태가 넓게 등록된 가게도 있어 상호명에 드러난 메뉴를 먼저 반영한다. */
const storeNameSuggestions: Array<StoreMenuSuggestion & { pattern: RegExp }> = [
  { pattern: /보쌈|족발/, category: "기타", menus: ["보쌈", "족발", "막국수", "쟁반국수"] },
  { pattern: /횟집|횟|활어|수산|회센터|사시미/, category: "일식", menus: ["광어회", "우럭회", "모둠회", "회덮밥", "매운탕"] },
  { pattern: /치킨|통닭|닭강정/, category: "치킨", menus: menuSuggestions["치킨"] },
  { pattern: /국밥|순대국/, category: "국물요리", menus: ["돼지국밥", "순대국", "내장국밥", "수육", "수육국밥"] },
  { pattern: /냉면|밀면/, category: "냉면류", menus: ["물냉면", "비빔냉면", "밀면", "만두"] },
  { pattern: /삼겹|갈비|고기|불고기/, category: "구이", menus: ["삼겹살", "목살", "갈비", "냉면", "된장찌개"] },
  { pattern: /떡볶이|김밥|분식/, category: "분식", menus: menuSuggestions["분식"] },
  { pattern: /초밥|스시|돈카츠|라멘|우동/, category: "일식", menus: ["초밥", "사시미", "돈카츠", "우동", "라멘"] },
  { pattern: /짜장|짬뽕|중화|중국/, category: "중식", menus: ["짜장면", "짬뽕", "탕수육", "볶음밥"] },
  { pattern: /피자|파스타|스테이크|레스토랑/, category: "양식", menus: ["파스타", "피자", "스테이크", "리조또"] },
  { pattern: /삼계|추어|장어|백숙/, category: "보양식", menus: ["삼계탕", "추어탕", "장어구이", "오리백숙"] },
  { pattern: /카페|커피/, category: "기타", menus: ["아메리카노", "카페라떼", "디저트"] },
];

function categoryFromStore(storeCategory: string | null): MenuCategory {
  const value = storeCategory?.replaceAll(" ", "") ?? "";
  if (/치킨|통닭|호프/.test(value)) return "치킨";
  if (/분식/.test(value)) return "분식";
  if (/중식/.test(value)) return "중식";
  if (/일식/.test(value)) return "일식";
  if (/양식|경양식|이탈리안/.test(value)) return "양식";
  if (/냉면|밀면/.test(value)) return "냉면류";
  if (/구이|갈비|고기/.test(value)) return "구이";
  if (/보양|삼계|추어/.test(value)) return "보양식";
  if (/국밥|탕|찌개|한식/.test(value)) return "국물요리";
  return "기타";
}

function suggestMenus(storeName: string | null, storeCategory: string | null): StoreMenuSuggestion {
  const value = `${storeName ?? ""} ${storeCategory ?? ""}`.replaceAll(" ", "");
  const matched = storeNameSuggestions.find((suggestion) => suggestion.pattern.test(value));
  if (matched) return matched;
  const category = categoryFromStore(storeCategory);
  return { category, menus: menuSuggestions[category] };
}

export default function StoreConfirmPage() {
  const { storeId: param } = useParams<{ storeId: string }>();
  const storeId = Number(param);
  const router = useRouter();
  const draft = useOnboardingDraft();
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const manualCategory = useRef(false);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema), values: { mainMenu: draft.mainMenu },
  });
  const store = draft.store?.id === storeId ? draft.store : null;
  const menu = draft.mainMenu.trim();
  const resolved = !!menu && draft.classifiedMenu === menu;
  const suggestion = menu
    ? { category: draft.menuCategory, menus: menuSuggestions[draft.menuCategory] }
    : suggestMenus(store?.name ?? null, store?.category ?? null);
  const suggestedCategory = suggestion.category;
  const examples = suggestion.menus;

  useEffect(() => {
    let active = true;
    if (!Number.isSafeInteger(storeId) || storeId <= 0) { router.replace("/search"); return; }
    if (readDraft().store?.id === storeId) return;
    getStore(storeId).then((result) => {
      if (!active) return;
      if (result.id !== storeId) { router.replace("/search"); return; }
      selectStore(result);
    }).catch(() => { if (active) router.replace("/search"); });
    return () => { active = false; };
  }, [storeId, router]);

  useEffect(() => {
    if (!store || !menu || readDraft().classifiedMenu === menu) return;
    let active = true;
    const timer = setTimeout(() => {
      classifyMenu(menu, store.category ?? undefined).then((result) => {
        if (!active || readDraft().mainMenu.trim() !== menu || manualCategory.current) return;
        saveDraft({ classification: result, menuCategory: result.menuCategory, classifiedMenu: menu });
        setError("");
      }).catch(() => {
        if (active) setError("자동 분류를 완료하지 못했어요. 다시 시도하거나 카테고리를 직접 선택해주세요.");
      });
    }, 400);
    return () => { active = false; clearTimeout(timer); };
  }, [menu, store, retry]);

  function changeMenu(value: string, category: MenuCategory = "기타") {
    manualCategory.current = false;
    setError("");
    saveDraft({ mainMenu: value, classifiedMenu: "", classification: null, menuCategory: category });
  }
  function confirmMenu({ mainMenu }: z.infer<typeof schema>) {
    saveDraft({ mainMenu });
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
  }
  function next() {
    const current = readDraft();
    if (current.store?.id === storeId && current.mainMenu.trim() && current.classifiedMenu === current.mainMenu.trim()) {
      router.push(`/store/${storeId}/connect`);
    }
  }

  return <OnboardingShell step={3} back="/search" title={<>대표 메뉴를<br />입력해주세요</>}
    description="입력하면 카테고리가 자동으로 분류돼요"
    action={<button className={styles.primary} disabled={!store || !resolved} onClick={next}>다음</button>}>
    {!store ? <p className={styles.status} role="status">가게 정보를 확인하고 있어요…</p> : <>
      <form onSubmit={form.handleSubmit(confirmMenu)} noValidate>
        <label htmlFor="mainMenu" className={styles.visuallyHidden}>대표 메뉴</label>
        <div className={styles.menuEntry}>
          <input id="mainMenu" className={styles.menuInput} placeholder="메뉴 이름 입력" enterKeyHint="done"
            aria-invalid={!!form.formState.errors.mainMenu}
            {...form.register("mainMenu")} onChange={(event) => {
              void form.register("mainMenu").onChange(event);
              changeMenu(event.target.value);
            }} />
        </div>
        {form.formState.errors.mainMenu && <p className={styles.error} role="alert">{form.formState.errors.mainMenu.message}</p>}
      </form>
      {examples.length > 0 && <div className={styles.chips} aria-label="대표 메뉴 예시">
        {examples.map((example) => <button key={example} className={styles.chip}
          aria-pressed={menu === example} onClick={() => { changeMenu(example, suggestedCategory); form.clearErrors(); }}>
          {example}<Icon name="chip" />
        </button>)}
      </div>}
      {menu && <div className={styles.classification}>
        <p role="status">{resolved ? `등록된 대표 메뉴: ${menu}` : error ? "카테고리를 직접 선택할 수 있어요." : "메뉴 카테고리를 확인하고 있어요…"}</p>
        {draft.classification && <p>
          자동 분류: {draft.classification.menuCategory}
          {draft.classification.confidence === "LOW" && " · 정확한 카테고리를 직접 선택해주세요."}
          {draft.classification.isDemoData && <span className={styles.demo}>데모 데이터</span>}
        </p>}
        <label htmlFor="menuCategory">메뉴 카테고리 확인·수정</label>
        <select id="menuCategory" className={styles.field} value={draft.menuCategory}
          onChange={(event) => {
            manualCategory.current = true;
            saveDraft({ menuCategory: event.target.value as MenuCategory, classifiedMenu: menu });
            setError("");
          }}>
          {MENU_CATEGORIES.map((category) => <option key={category}>{category}</option>)}
        </select>
        {!resolved && <button className={styles.secondary} onClick={() => {
          manualCategory.current = true; saveDraft({ classifiedMenu: menu }); setError("");
        }}>이 카테고리로 등록</button>}
      </div>}
      {error && <div role="alert" className={styles.error}>
        <p>{error}</p><button className={styles.secondary} onClick={() => { setError(""); setRetry((n) => n + 1); }}>분류 다시 시도</button>
      </div>}
      {store.isDemoData && <p className={styles.status}>{store.name} <span className={styles.demo}>데모 데이터</span></p>}
    </>}
  </OnboardingShell>;
}
