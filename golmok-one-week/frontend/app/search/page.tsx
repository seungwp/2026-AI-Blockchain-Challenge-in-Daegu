"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import OnboardingShell, { Icon } from "@/components/onboarding/OnboardingShell";
import styles from "@/components/onboarding/onboarding.module.css";
import { createStoreFromAddress, searchStores } from "@/lib/api";
import { saveDraft, selectStore, useOnboardingDraft } from "@/lib/onboarding";
import type { Store, ViewState } from "@/types";

const schema = z.object({ keyword: z.string().trim().min(1, "상호명 또는 주소를 입력해주세요.") });
const manualSchema = z.object({ address: z.string().trim().min(1, "가게 주소를 입력해주세요.") });

export default function SearchPage() {
  const router = useRouter();
  const draft = useOnboardingDraft();
  const [state, setState] = useState<ViewState>("initial");
  const [stores, setStores] = useState<Store[] | null>(null);
  const [error, setError] = useState("");
  const request = useRef(0);
  const manualLock = useRef(false);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema), values: { keyword: draft.keyword },
  });
  const manual = useForm<z.infer<typeof manualSchema>>({
    resolver: zodResolver(manualSchema), defaultValues: { address: "" },
  });
  useEffect(() => () => { request.current++; }, []);

  async function search({ keyword }: z.infer<typeof schema>) {
    const id = ++request.current;
    saveDraft({ keyword, store: null });
    setStores(null);
    setState("loading");
    setError("");
    try {
      const results = await searchStores(keyword);
      if (request.current !== id) return;
      setStores(results);
      setState(results.length ? "success" : "empty");
    } catch (cause) {
      if (request.current !== id) return;
      setState("error");
      setError(cause instanceof Error ? cause.message : "검색하지 못했습니다. 다시 시도해주세요.");
    }
  }

  async function registerAddress({ address }: z.infer<typeof manualSchema>) {
    if (manualLock.current) return;
    manualLock.current = true;
    const id = ++request.current;
    setError("");
    try {
      const store = await createStoreFromAddress(address);
      if (request.current !== id) return;
      selectStore(store);
      router.push(`/store/${store.id}/confirm`);
    } catch (cause) {
      if (request.current === id) setError(cause instanceof Error ? cause.message : "주소 등록에 실패했습니다.");
    } finally { manualLock.current = false; }
  }

  const results = stores ?? (draft.store ? [draft.store] : []);
  const busy = state === "loading" || manual.formState.isSubmitting;
  return <OnboardingShell step={2} back="/" title={<>가게 위치를<br />검색해주세요</>}
    description="인허가 정보로 정확한 가게를 찾아드려요"
    action={<button className={styles.primary} disabled={!draft.store || busy}
      onClick={() => draft.store && router.push(`/store/${draft.store.id}/confirm`)}>다음</button>}>
    <form onSubmit={(event) => void form.handleSubmit(search)(event)} noValidate>
      <label className={styles.visuallyHidden} htmlFor="keyword">상호명 또는 주소</label>
      <div className={styles.search}>
        <button className={styles.iconButton} type="submit" aria-label="가게 검색" disabled={busy}><Icon name="search" /></button>
        <input id="keyword" placeholder="예: 교촌치킨 / 대구 중구" enterKeyHint="search"
          disabled={manual.formState.isSubmitting}
          aria-invalid={!!form.formState.errors.keyword} aria-describedby="search-message"
          {...form.register("keyword")} onChange={(event) => {
            void form.register("keyword").onChange(event);
            request.current++;
            saveDraft({ keyword: event.target.value, store: null });
            setStores(null); setState("initial"); setError("");
          }} />
      </div>
      {form.formState.errors.keyword && <p className={styles.error} role="alert">{form.formState.errors.keyword.message}</p>}
    </form>
    <div id="search-message" aria-live="polite">
      {state === "loading" && <p className={styles.status} role="status">가게를 찾고 있어요…</p>}
      {state === "initial" && !draft.store && <p className={styles.status}>상호명이나 주소로 내 가게를 찾아보세요.</p>}
      {state === "empty" && <p className={styles.status}>검색 결과가 없어요. 주소를 직접 입력할 수 있어요.</p>}
    </div>
    {error && <p role="alert" className={styles.error}>{error}</p>}
    <ul className={styles.results} aria-label="가게 검색 결과">
      {results.map((store) => <li key={store.id}>
        <button className={`${styles.store} ${draft.store?.id === store.id ? styles.selected : ""}`}
          type="button" aria-pressed={draft.store?.id === store.id} onClick={() => selectStore(store)}>
          <span className={styles.pin}><Icon name="location" /></span>
          <span className={styles.storeText}>{store.name}
            <small>{store.roadAddress ?? store.address} · {store.category ?? "업종 정보 없음"}</small>
            {store.isDemoData && <span className={styles.demo}>데모 데이터</span>}
          </span>
        </button>
      </li>)}
    </ul>
    {(state === "empty" || state === "error") && <form className={styles.manual} onSubmit={(event) => void manual.handleSubmit(registerAddress)(event)} noValidate>
      <label htmlFor="address">가게 주소 직접 입력</label>
      <input id="address" className={styles.field} placeholder="예: 대구광역시 중구 동성로 1"
        autoComplete="street-address" enterKeyHint="done" disabled={manual.formState.isSubmitting}
        aria-invalid={!!manual.formState.errors.address} {...manual.register("address")} />
      {manual.formState.errors.address && <p className={styles.error} role="alert">{manual.formState.errors.address.message}</p>}
      <button className={styles.secondary} disabled={manual.formState.isSubmitting}>
        {manual.formState.isSubmitting ? "주소 확인 중…" : "이 주소로 계속하기"}
      </button>
    </form>}
  </OnboardingShell>;
}
