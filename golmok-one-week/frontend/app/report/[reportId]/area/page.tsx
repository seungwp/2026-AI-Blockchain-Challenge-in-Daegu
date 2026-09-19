"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import CommercialAreaSummary from "@/components/report/CommercialAreaSummary";
import StateMessage from "@/components/common/StateMessage";
import { useReport } from "@/lib/history";
import styles from "@/components/report/main-dashboard.module.css";

/** 내 페이지 › 주변 생활권 분석 (1차 프레임에 별도 화면 없음 — 상세 헤더 스타일 재사용) */
export default function AreaPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const { state, report } = useReport(reportId);
  return <main className={styles.main}>
    <header className={styles.backHeader}>
      <Link href={`/report/${reportId}/my`} aria-label="내 페이지로">‹</Link>
      <div><h1>주변 생활권</h1>{report && <p>{report.store.name} · {report.store.address ?? ""}</p>}</div>
    </header>
    {!report ? <StateMessage state={state} message={state === "error" ? "생활권 정보를 불러오지 못했습니다." : undefined} />
      : <div className={styles.panel}><CommercialAreaSummary area={report.commercialArea} /></div>}
  </main>;
}
