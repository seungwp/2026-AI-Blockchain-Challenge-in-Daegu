import Image from "next/image";
import Link from "next/link";
import { ActionList } from "./ActionCard";
import type { AnalysisReport } from "@/types";
import styles from "./main-dashboard.module.css";

/** 홈 탭 (Figma 1차 › 홈화면 73:241) */
export default function MainDashboard({ report, reportId }: { report: AnalysisReport; reportId: number }) {
  const count = report.topActions.length;
  // 기존에 저장된 리포트의 상권 등급 문장도 화면에서는 숨긴다.
  const summary = report.summary.replace(/ 주변 경쟁[^.]*입니다\./g, "");
  const fallbackSummary = (() => {
    const weather = report.weather[0];
    if (!weather) return "이번 주 공공데이터를 바탕으로 운영 준비 항목을 정리했어요.";
    const temperature = weather.tempMax !== null && weather.tempMin !== null
      ? `최고 ${weather.tempMax}° / 최저 ${weather.tempMin}°`
      : "기온 정보 확인 중";
    const rain = weather.precipitationProbability !== null ? `, 강수확률 ${weather.precipitationProbability}%` : "";
    return `${weather.date} ${weather.condition} 예보(${temperature}${rain})를 포함해 이번 주 운영 준비 항목을 정리했어요.`;
  })();
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
    {report.isDemoData && <section className={styles.dataNotice} role="status">
      <strong>실데이터를 모두 불러오지 못했어요</strong>
      <p>{report.demoNotice ?? "일부 항목은 예시 값입니다. 실제 운영 판단에는 사용하지 마세요."}</p>
    </section>}
    <section className={styles.posCard} aria-label="매출 데이터 연결">
      <div><strong>매출 데이터가 연결되지 않았어요</strong><p>POS기 연동을 통해 더욱 정확한 처방을 받아보세요</p></div>
      <span className={styles.pill} title="준비 중">준비 중</span>
    </section>
    <div className={styles.sectionTitle}><h2>이번 주 처방</h2><span>{count}건</span></div>
    <section className={styles.card} aria-label="AI 요약">
      <p className={styles.aiLabel}>AI 요약</p>
      <p className={styles.aiText}>{summary || fallbackSummary}</p>
    </section>
    <ActionList report={report} />
    <p className={styles.disclaimer}>{report.disclaimer}</p>
  </main>;
}
