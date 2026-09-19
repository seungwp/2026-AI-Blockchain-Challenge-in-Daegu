"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import OnboardingShell from "@/components/onboarding/OnboardingShell";
import styles from "@/components/onboarding/onboarding.module.css";
import { createReport } from "@/lib/api";
import { readDraft, useOnboardingDraft } from "@/lib/onboarding";

export default function ConnectPage() {
  const { storeId: param } = useParams<{ storeId: string }>();
  const storeId = Number(param);
  const router = useRouter();
  const draft = useOnboardingDraft();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  const alive = useRef(true);
  const ready = draft.store?.id === storeId && !!draft.mainMenu.trim() && draft.classifiedMenu === draft.mainMenu.trim();

  useEffect(() => {
    alive.current = true;
    const current = readDraft();
    if (!Number.isSafeInteger(storeId) || storeId <= 0 || current.store?.id !== storeId) {
      router.replace("/search");
    } else if (!current.mainMenu.trim() || current.classifiedMenu !== current.mainMenu.trim()) {
      router.replace(`/store/${storeId}/confirm`);
    }
    return () => { alive.current = false; };
  }, [storeId, router]);

  async function analyze() {
    if (locked.current || !ready) return;
    locked.current = true;
    setPending(true);
    setError("");
    try {
      const report = await createReport(storeId, draft.mainMenu.trim(), draft.menuCategory);
      if (alive.current) router.push(`/report/${report.reportId}`);
    } catch (cause) {
      if (alive.current) {
        setError(cause instanceof Error ? cause.message : "분석을 완료하지 못했어요. 다시 시도해주세요.");
        setPending(false);
      }
      locked.current = false;
    }
  }

  // 사이트맵 5. 분석 로딩 — 오류가 나면 다시 연결 선택 화면으로 돌아간다.
  if (pending) return <OnboardingShell step={4} title={<>이번 주 처방을<br />준비하고 있어요</>}
    description={`${draft.store?.name ?? "가게"} · ${draft.mainMenu}`}>
    <ol className={styles.loadingSteps} role="status" aria-label="분석 진행">
      <li>이번 주 날씨 예보 확인</li>
      <li>가까운 축제·행사 확인</li>
      <li>주변 상권·식자재 가격 확인</li>
    </ol>
  </OnboardingShell>;

  return <OnboardingShell step={4} back={`/store/${storeId}/confirm`}
    eyebrow="마지막 단계예요" title="가게 데이터를 연결할까요?"
    description="연결 없이도 처방은 바로 받을 수 있어요"
    action={<button className={styles.primary} disabled={!ready || pending} onClick={analyze}>이번주 활기차게 시작하기</button>}>
    {!ready ? <p className={styles.status} role="status">입력한 정보를 확인하고 있어요…</p> : <>
      <section className={`${styles.connection} ${styles.recommended}`} aria-labelledby="public-title">
        <div><h2 id="public-title">공공데이터로 바로 시작</h2>
        <p>날씨·행사·상권·식자재 기준으로 지금 바로 이번 주 처방을 받아요.</p></div><span className={styles.connectionCheck}>✓</span>
      </section>
      <section className={`${styles.connection} ${styles.preparing}`} aria-labelledby="pos-title">
        <div><h2 id="pos-title">가게데이터 연결하기 <small>준비 중</small></h2>
        <p>카드매출·포스·배달앱을 연결하면 동네 평균이 아니라 우리 가게 기준으로 정확해져요.</p></div><span className={styles.connectionRadio} />
      </section>
      <p className={styles.connectionHint}>연결은 나중에 ‘내 페이지’에서 언제든 할 수 있어요</p>
      {error && <p className={styles.error} role="alert">{error}</p>}
    </>}
  </OnboardingShell>;
}
