import Image from "next/image";
import Link from "next/link";
import { ActionList } from "./ActionCard";
import type { AnalysisReport } from "@/types";
import styles from "./main-dashboard.module.css";

/** 홈 탭 (Figma 1차 › 홈화면 73:241) */
export default function MainDashboard({ report, reportId }: { report: AnalysisReport; reportId: number }) {
  const count = report.topActions.length;
  return <main className={styles.main}>
    <header className={styles.header}>
      <Link href={`/report/${reportId}/my`} className={styles.storeName} aria-label={`${report.store.name} · 내 페이지`}>
        <Image src="/main/pin.svg" width={32} height={32} alt="" /><strong>{report.store.name}</strong>
      </Link>
      <button type="button" className={styles.iconButton} aria-label="알림 기능은 준비 중입니다." title="준비 중"><Image src="/main/bell.svg" width={24} height={24} alt="" /></button>
    </header>
    <section className={styles.hero} aria-labelledby="weekly-title">
      <h1 id="weekly-title">이번 주,<br />뭘 챙겨야 할까요?</h1>
      <p>이번 주 처방 {count}건이 도착했어요</p>
    </section>
    <section className={styles.posCard} aria-label="매출 데이터 연결">
      <div><strong>매출 데이터가 연결되지 않았어요</strong><p>POS기 연동을 통해 더욱 정확한 처방을 받아보세요</p></div>
      <span className={styles.pill} title="준비 중">준비 중</span>
    </section>
    <div className={styles.sectionTitle}><h2>이번 주 처방</h2><span>{count}건</span></div>
    {(report.aiSummary || report.summary) && <section className={styles.card} aria-label="요약">
      <p className={styles.aiLabel}>{report.aiSummary ? "AI 요약" : "이번 주 요약"}</p>
      <p className={styles.aiText}>{report.aiSummary ?? report.summary}</p>
    </section>}
    <ActionList report={report} />
    <p className={styles.disclaimer}>{report.disclaimer}</p>
  </main>;
}
