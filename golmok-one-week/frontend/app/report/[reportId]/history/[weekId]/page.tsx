"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import StateMessage from "@/components/common/StateMessage";
import { ActionList } from "@/components/report/ActionCard";
import { shortDate, useReport, weekLabel } from "@/lib/history";
import styles from "@/components/report/main-dashboard.module.css";

/** 처방이력 상세 — 읽기 전용 (Figma 1차 73:376) */
export default function PastWeekPage() {
  const params = useParams<{ reportId: string; weekId: string }>();
  const { state, report } = useReport(Number(params.weekId));
  return <main className={styles.main}>
    <header className={styles.backHeader}>
      <Link href={`/report/${params.reportId}/history`} aria-label="처방이력으로">‹</Link>
      {report && <div><h1>{weekLabel(report.analysisStartDate)}</h1><p>{shortDate(report.analysisStartDate)} – {shortDate(report.analysisEndDate)}</p></div>}
    </header>
    {!report ? <StateMessage state={state} message={state === "error" ? "지난 리포트를 불러오지 못했습니다." : undefined} /> : <>
      <span className={styles.readOnly}>지난 처방 · 읽기 전용</span>
      <ActionList report={report} />
      <p className={styles.endNote}>이 주의 처방은 여기까지예요</p>
    </>}
  </main>;
}
