"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import ChatWidget from "./ChatWidget";
import CommercialAreaSummary from "./CommercialAreaSummary";
import DailyGuideList from "./DailyGuideList";
import FestivalList from "./FestivalList";
import IngredientPriceList from "./IngredientPriceList";
import SourceList from "./SourceList";
import WeatherList from "./WeatherList";
import Disclaimer from "@/components/common/Disclaimer";
import type { AnalysisReport, ConditionType, Recommendation } from "@/types";
import styles from "./main-dashboard.module.css";

const iconByCondition: Partial<Record<ConditionType, string>> = {
  RAIN: "weather", HOT: "weather", COLD: "weather", FESTIVAL: "festival",
  HOLIDAY: "festival", PRICE_SPIKE: "ingredient", COMPETITION: "competition",
};
function label(item: Recommendation) {
  if (["RAIN", "HOT", "COLD"].includes(item.conditionType)) return "날씨";
  if (["FESTIVAL", "HOLIDAY"].includes(item.conditionType)) return "행사";
  if (item.conditionType === "PRICE_SPIKE") return "식자재";
  if (item.conditionType === "COMPETITION") return "경쟁";
  return item.type === "STAFFING" ? "인력" : item.type === "DELIVERY" ? "배달" : "운영";
}
function dateRange(report: AnalysisReport) {
  const start = new Date(report.analysisStartDate + "T00:00:00");
  const end = new Date(report.analysisEndDate + "T00:00:00");
  return (start.getMonth() + 1) + "월 " + Math.ceil((start.getDate() + start.getDay()) / 7) + "주차 · " + start.getDate() + "~" + end.getDate() + "일";
}
export default function MainDashboard({ report, reportId }: { report: AnalysisReport; reportId: number }) {
  const [details, setDetails] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const topActions = report.topActions.slice(0, 4);
  const remainingCount = Math.max(0, report.topActions.length - topActions.length);
  return <main className={styles.main}>
    <header className={styles.header}>
      <Link href="/search" className={styles.brand} aria-label="다른 가게 분석">골목 한 주</Link>
      <div className={styles.headerActions}>
        <button type="button" className={styles.roundButton} aria-label="알림 기능은 준비 중입니다." title="준비 중"><Image src="/main/bell.svg" width={18} height={18} alt="" /></button>
        <Link href="/search" className={styles.storeButton} aria-label="다른 가게 분석"><span>{report.store.name}</span><Image src="/main/chevron.svg" width={14} height={14} alt="" /></Link>
      </div>
    </header>
    <section className={styles.hero} aria-labelledby="weekly-title"><h1 id="weekly-title">이번 주,<br />뭘 챙겨야 할까요?</h1><p>이번 주 운영 참고 항목 {report.topActions.length}건을 정리했어요</p></section>
    <section className={styles.weekCard} aria-label="분석 기간과 매출 데이터 연결">
      <div className={styles.weekSwitcher}><span className={styles.arrow}><Image src="/main/arrow.svg" width={13} height={14} alt="" /></span><strong>{dateRange(report)}</strong><span className={styles.arrow}><Image src="/main/arrow.svg" width={13} height={14} alt="" /></span></div>
      <div className={styles.connectionNotice}><div><strong>매출 데이터가 아직 연결되지 않았어요</strong><p>연결되면 실제 매출 흐름은 이 자리에서 확인할 수 있어요</p></div><button type="button" className={styles.connectButton} title="준비 중" aria-label="매출 데이터 연결 기능은 준비 중입니다.">준비 중</button></div>
    </section>
    <section className={styles.actions} aria-labelledby="actions-title">
      <h2 id="actions-title">이번 주 처방</h2>
      {!topActions.length && <p className={styles.empty}>이번 주 특별히 강조할 점검 항목이 없습니다.</p>}
      {topActions.map((item, index) => {
        const category = label(item); const icon = iconByCondition[item.conditionType] ?? "weather";
        return <article className={[styles.actionCard, styles[icon]].filter(Boolean).join(" ")} key={item.title + index}>
          <div className={styles.actionTop}><span className={styles.category}><span className={styles.categoryIcon}><Image src={"/main/" + icon + ".svg"} width={14} height={14} alt="" /></span>{category}</span><span className={[styles.priority, item.priority === "HIGH" ? styles.high : styles.medium].join(" ")}>{item.priority === "HIGH" ? "★★★ 필수" : "★★ 확인"}</span></div>
          <p className={styles.basis}>{item.basis ?? item.title}</p><p className={styles.recommendation}>{item.text}</p>
          <details className={styles.evidence}><summary>근거보기 <Image src="/main/next.svg" width={13} height={13} alt="" /></summary><p>{item.basis ?? "리포트에 포함된 운영 참고 정보입니다."}</p><p>출처 ID: {item.sourceIds.length ? item.sourceIds.join(", ") : "-"}</p></details>
        </article>;
      })}
      <button className={styles.moreButton} type="button" onClick={() => setDetails((open) => !open)} aria-expanded={details} aria-controls="full-report">{details ? "간단히 보기" : remainingCount ? "추가 처방 " + remainingCount + "개와 전체 리포트 보기" : "전체 리포트 보기"}</button>
    </section>
    {details && <section id="full-report" className={styles.fullReport} aria-label="전체 리포트"><WeatherList days={report.weather} /><CommercialAreaSummary area={report.commercialArea} /><FestivalList festivals={report.festivals} /><IngredientPriceList prices={report.ingredientPrices} /><DailyGuideList guides={report.dailyGuides} /><SourceList sources={report.sources} /><Disclaimer text={report.disclaimer} /></section>}
    <nav className={styles.bottomNav} aria-label="주요 메뉴">
      <Link href={"/report/" + reportId} className={styles.activeNav}><Image src="/main/home.svg" width={20} height={20} alt="" />홈</Link>
      <button type="button" title="처방 이력은 준비 중입니다."><Image src="/main/history.svg" width={20} height={20} alt="" />처방이력</button>
      <button type="button" onClick={() => setChatOpen(true)} aria-label="리포트 챗봇 열기"><Image src="/main/chat.svg" width={20} height={20} alt="" />챗봇</button>
      <Link href="/search"><span className={styles.profileIcon} aria-hidden="true">♙</span>가게 변경</Link>
    </nav><ChatWidget reportId={reportId} open={chatOpen} onOpenChange={setChatOpen} hideLauncher />
  </main>;
}
