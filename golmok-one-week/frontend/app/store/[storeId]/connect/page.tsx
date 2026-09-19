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
  const [preparing, setPreparing] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  const alive = useRef(true);
  const notice = useRef<HTMLDivElement>(null);
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
  useEffect(() => { if (preparing) notice.current?.focus(); }, [preparing]);

  async function analyze() {
    if (locked.current || !ready) return;
    locked.current = true;
    setPending(true);
    setError("");
    try {
      const report = await createReport(storeId, draft.mainMenu.trim(), draft.menuCategory);
      if (alive.current) router.push(`/report/${report.reportId}/area`);
    } catch (cause) {
      if (alive.current) {
        setError(cause instanceof Error ? cause.message : "분석을 완료하지 못했어요. 다시 시도해주세요.");
        setPending(false);
      }
      locked.current = false;
    }
  }

  return <OnboardingShell step={4} back={`/store/${storeId}/confirm`}
    title={<>가게 데이터<br />연결하시겠어요?</>}
    description="연결 없이도 이번 주 처방을 바로 받을 수 있어요">
    {!ready ? <p className={styles.status} role="status">입력한 정보를 확인하고 있어요…</p> : <>
      <section className={`${styles.connection} ${styles.recommended}`} aria-labelledby="public-title">
        <h2 id="public-title">연동 없이 분석하기</h2>
        <p>공공데이터만으로 이번 주 처방을 확인해요.<br />가게와 대표 메뉴에 맞춰 준비할 일을 알려드려요.</p>
        <button className={styles.cardAction} disabled={pending} onClick={analyze}>
          {pending ? "분석 중…" : error ? "다시 분석하기" : "바로 시작하기"}
        </button>
      </section>
      <section className={`${styles.connection} ${styles.preparing}`} aria-labelledby="pos-title">
        <h2 id="pos-title">포스기 연결하기</h2>
        <p>포스기 연결 기능은 준비 중이에요.<br />지금은 공공데이터로 먼저 시작할 수 있어요.</p>
        <button className={styles.cardAction} disabled={pending} aria-expanded={preparing}
          aria-controls="pos-notice" onClick={() => setPreparing((value) => !value)}>연결해보기</button>
        {preparing && <div id="pos-notice" ref={notice} tabIndex={-1} className={styles.posNotice} role="region" aria-label="POS 연결 준비 중 안내">
          <p>아직 포스기에 연결하거나 매출 정보를 가져오지 않아요. 공공데이터 분석은 바로 이용할 수 있어요.</p>
          <button className={styles.cardAction} disabled={pending} onClick={analyze}>공공데이터로 분석하기</button>
        </div>}
      </section>
      {pending && <p className={styles.status} role="status">이번 주 날씨·행사·상권 정보를 모으고 있어요. 잠시만 기다려주세요.</p>}
      {error && <p className={styles.error} role="alert">{error}</p>}
      <p className={styles.disclaimer}>운영 참고용 안내이며, 실제 매출을 예측하거나 보장하지 않습니다.</p>
    </>}
  </OnboardingShell>;
}
