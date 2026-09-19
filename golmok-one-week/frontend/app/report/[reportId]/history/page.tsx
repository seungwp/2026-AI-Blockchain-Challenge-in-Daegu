"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import StateMessage from "@/components/common/StateMessage";
import { readHistory, shortDate, useReport, weekLabel } from "@/lib/history";
import styles from "@/components/report/main-dashboard.module.css";

/** 처방이력 리스트 (Figma 1차 73:330) */
export default function HistoryPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const { state, report } = useReport(reportId);
  // useReport가 현재 리포트를 기록한 뒤 읽는다.
  const weeks = report ? readHistory().filter((w) => w.storeId === report.store.id) : [];
  return <main className={styles.main}>
    <header className={styles.pageTitle}><h1>처방이력</h1><p>지난 주들의 처방을 다시 볼 수 있어요</p></header>
    {!report ? <StateMessage state={state} message={state === "error" ? "처방 이력을 불러오지 못했습니다." : undefined} /> : <>
      <ul className={styles.weekList}>
        {weeks.map((w) => <li key={w.reportId}>
          <Link href={w.reportId === reportId ? "/report/" + reportId : `/report/${reportId}/history/${w.reportId}`} className={styles.weekItem}>
            <div>
              <strong>{weekLabel(w.start)}</strong>
              <small>{shortDate(w.start)} – {shortDate(w.end)}</small>
              <span>처방 {w.count}건</span>
            </div>
            <span className={styles.chevron} aria-hidden="true">›</span>
          </Link>
        </li>)}
      </ul>
      <p className={styles.endNote}>{weeks.length > 1 ? "더 지난 이력은 없어요" : "다음 주부터 지난 처방이 여기에 쌓여요"}</p>
    </>}
  </main>;
}
