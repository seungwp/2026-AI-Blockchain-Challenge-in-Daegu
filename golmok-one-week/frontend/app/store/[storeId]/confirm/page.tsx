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
const examples = ["떡볶이", "튀김", "순대"];

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

  function changeMenu(value: string) {
    manualCategory.current = false;
    setError("");
    saveDraft({ mainMenu: value, classifiedMenu: "", classification: null, menuCategory: "기타" });
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
          <button className={`${styles.iconButton} ${styles.enter}`} type="submit" aria-label="대표 메뉴 등록"><Icon name="enter" /></button>
        </div>
        {form.formState.errors.mainMenu && <p className={styles.error} role="alert">{form.formState.errors.mainMenu.message}</p>}
      </form>
      <div className={styles.chips} aria-label="대표 메뉴 예시">
        {examples.map((example) => <button key={example} className={styles.chip}
          aria-pressed={menu === example} onClick={() => { changeMenu(example); form.clearErrors(); }}>
          {example}<Icon name="chip" />
        </button>)}
      </div>
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
