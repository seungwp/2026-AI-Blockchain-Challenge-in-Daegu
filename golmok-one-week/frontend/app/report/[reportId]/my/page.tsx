"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import StateMessage from "@/components/common/StateMessage";
import { readHistory, useReport } from "@/lib/history";
import { saveDraft, selectStore } from "@/lib/onboarding";
import styles from "@/components/report/main-dashboard.module.css";

/** 내 페이지 (Figma 1차 73:499) */
export default function MyPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const { state, report } = useReport(reportId);
  const [open, setOpen] = useState<"" | "pos" | "alarm" | "about">("");
  const toggle = (key: typeof open) => setOpen((v) => (v === key ? "" : key));
  if (!report) return <main className={styles.main}><StateMessage state={state} message={state === "error" ? "가게 정보를 불러오지 못했습니다." : undefined} /></main>;

  const { store, commercialArea: area } = report;
  const storeCount = new Set(readHistory().map((w) => w.storeId)).size || 1;
  return <main className={styles.main}>
    <header className={styles.pageTitle}><h1>내 페이지</h1></header>
    <section className={styles.card} aria-label="내 가게 정보">
      <div className={styles.storeHead}>
        <strong>{store.name}</strong>
        {/* 수정은 온보딩 메뉴 단계를 재사용한다: 현재 가게·메뉴를 채워 넣고 이동 */}
        <Link href={`/store/${store.id}/confirm`} className={styles.link} onClick={() => {
          selectStore(store);
          saveDraft({ mainMenu: report.mainMenu, menuCategory: report.menuCategory, classifiedMenu: report.mainMenu });
        }}>수정 ›</Link>
      </div>
      <p className={styles.storeMeta}>{[store.category, store.roadAddress ?? store.address].filter(Boolean).join(" · ")}{store.isDemoData && " · 데모 데이터"}</p>
      <p className={styles.menuChip}>대표 메뉴 <span className={styles.badge}>{report.mainMenu} · {report.menuCategory}</span></p>
      <div className={styles.divider} />
      <Link href={`/report/${reportId}/area`} className={styles.areaLink}>
        주변 생활권 분석<span>{area ? `반경 500m · 경쟁 강도 ${area.competitionLevel ?? "-"}` : "위치 정보 없음"} ›</span>
      </Link>
    </section>
    <section className={styles.card} aria-labelledby="pos-title">
      <div className={styles.cardTop}><h2 id="pos-title" className={styles.cardTitle}>가게데이터 연결</h2><span className={`${styles.badge} ${styles.badgeMuted}`}>연결 전</span></div>
      <p className={styles.body}>카드매출·포스·배달앱 주문을 연결하면 동네 평균이 아니라 우리 가게 기준으로 알려드려요.</p>
      <button type="button" className={styles.textButton} aria-expanded={open === "pos"} onClick={() => toggle("pos")}>연결하기 ›</button>
      {open === "pos" && <p className={styles.notice} role="status">가게 데이터 연결은 준비 중이에요. 지금은 공공데이터로 처방을 받을 수 있어요.</p>}
    </section>
    <section className={styles.settings} aria-label="설정">
      <button type="button" className={styles.settingRow} aria-expanded={open === "alarm"} onClick={() => toggle("alarm")}>알림 설정<span>월요일 오전 ›</span></button>
      {open === "alarm" && <p className={styles.notice} role="status">매주 월요일 오전 처방 알림은 준비 중이에요.</p>}
      <Link href="/search" className={styles.settingRow}>가게 추가·전환<span>가게 {storeCount}곳 ›</span></Link>
      <button type="button" className={styles.settingRow} aria-expanded={open === "about"} onClick={() => toggle("about")}>서비스 안내<span>›</span></button>
      {open === "about" && <p className={styles.notice}>{report.disclaimer} 날씨·축제·식자재 가격·상권 공공데이터를 근거로 하며, 매출을 예측하지 않습니다.</p>}
    </section>
    <p className={styles.endNote}>장사메이트 · 운영 참고용 서비스 · 매출을 보장하지 않아요</p>
  </main>;
}
