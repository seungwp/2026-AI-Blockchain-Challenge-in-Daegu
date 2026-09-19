"use client";

import { useRef } from "react";
import type { CSSProperties, ReactNode } from "react";
import type { AnalysisReport, IngredientPrice, Recommendation, Source } from "@/types";
import styles from "./main-dashboard.module.css";

const WEATHER = ["RAIN", "HOT", "COLD"];
function kind(item: Recommendation) {
  if (WEATHER.includes(item.conditionType)) return { label: "날씨", icon: "cloud" };
  if (item.conditionType === "FESTIVAL" || item.conditionType === "HOLIDAY") return { label: "행사", icon: "calendar" };
  if (item.conditionType === "PRICE_SPIKE") return { label: "식자재", icon: "cart" };
  if (item.conditionType === "COMPETITION") return { label: "경쟁", icon: "competition" };
  if (item.type === "STAFFING") return { label: "인력", icon: "calendar" };
  return { label: item.type === "DELIVERY" ? "배달" : "운영", icon: "cloud" };
}
const monthDay = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  return `${d.getMonth() + 1}월 ${d.getDate()}일(${"일월화수목금토"[d.getDay()]})`;
};
const pct = (ratio: number) => (ratio >= 0 ? "+" : "") + Math.round(ratio * 100) + "%";
/** 출처 → "서울 음식점 카드매출과 … 분석한 연구 (한국자료분석학회, 2019)" — sources.csv 값만 조합 */
const explain = (s: Source) => `${s.description ?? s.title} (${[s.organization, s.publicationYear].filter(Boolean).join(", ") || s.title})`;
/** "비 예보 · 포장·배달 점검" → "포장·배달 점검" */
const actionOf = (title: string) => title.split(" · ").pop() ?? title;

function CardFrame({ label, icon, badge, children }: { label: string; icon: string; badge: ReactNode; children: ReactNode }) {
  return <article className={styles.card}>
    <div className={styles.cardTop}>
      <span className={styles.category}><span className={styles.categoryIcon}><span style={{ "--icon": `url(/main/${icon}.svg)` } as CSSProperties} /></span>{label}</span>
      {badge}
    </div>
    {children}
  </article>;
}
const Priority = ({ item }: { item: Recommendation }) => <span className={styles.badge}>{item.priority === "HIGH" ? "★★★ 필수" : "★★ 확인"}</span>;

function PriceRows({ prices }: { prices: IngredientPrice[] }) {
  const first = prices[0];
  return <>
    {prices.map((p) => {
      const ratio = p.vsPreviousWeekRatio ?? null;
      const change = ratio !== null ? "전주 대비 " + pct(ratio) : p.vsNormalRatio !== null ? "평년 대비 " + pct(p.vsNormalRatio) : "";
      return <div className={styles.priceRow} key={p.item}>
        <div><span>{p.item} {p.unit ?? ""}</span>{change && <small className={(ratio ?? p.vsNormalRatio ?? 0) >= 0.1 ? styles.up : undefined}>{change}</small>}</div>
        <strong>{p.price !== null ? p.price.toLocaleString("ko-KR") + "원" : "-"}</strong>
      </div>;
    })}
    {first && <p className={styles.priceNote}>{first.priceBasis ?? "KAMIS 대구 소매가 기준"}{first.priceDate && !first.isDemoData ? " · " + monthDay(first.priceDate) : ""}</p>}
  </>;
}

/** 처방이 없어도 가격 정보가 있으면 보여주는 식자재 참고 카드. */
export function IngredientCard({ prices }: { prices: IngredientPrice[] }) {
  if (!prices.length) return null;
  return <CardFrame label="식자재" icon="cart" badge={<span className={`${styles.badge} ${styles.badgeMuted}`}>참고</span>}>
    <PriceRows prices={prices} />
  </CardFrame>;
}

/** 홈·지난 주 상세 공통 카드 목록. 식자재 처방이 없으면 가격 참고 카드를 끝에 붙인다. */
export function ActionList({ report }: { report: AnalysisReport }) {
  return <>
    {!report.topActions.length && <p className={styles.empty}>이번 주 특별히 강조할 점검 항목이 없어요.</p>}
    {report.topActions.map((item, i) => <ActionCard key={item.title + i} item={item} report={report} />)}
    {!report.topActions.some((a) => a.conditionType === "PRICE_SPIKE") && <IngredientCard prices={report.ingredientPrices} />}
  </>;
}

/** 이번 주 처방 카드(Figma 골목/Card-요일가이드·행사·식자재). 근거보기는 바텀 시트(native dialog). */
export default function ActionCard({ item, report }: { item: Recommendation; report: AnalysisReport }) {
  const sheet = useRef<HTMLDialogElement>(null);
  const { label, icon } = kind(item);
  const cited = report.sources.filter((s) => item.sourceIds.includes(s.id));
  // 대표 근거 1개만 사람이 읽는 문장으로 보여준다(연구·지자체 자료 우선). 내부 출처 번호는 노출하지 않는다.
  const main = cited.find((s) => s.sourceType === "PAPER" || s.sourceType === "FESTIVAL") ?? cited[0];
  const day = WEATHER.includes(item.conditionType) ? report.weather.find((w) => w.date === item.date) : undefined;
  const festival = label === "행사" ? report.festivals[0] : undefined;

  return <CardFrame label={label} icon={icon} badge={<Priority item={item} />}>
    {day ? <p className={styles.condition}>{day.condition} · 최고 {day.tempMax ?? "-"}° / 최저 {day.tempMin ?? "-"}° · 강수확률 {day.precipitationProbability ?? "-"}%</p>
      : festival ? <>
        <p className={styles.condition}>{festival.name} · {monthDay(festival.startDate)} ~ {monthDay(festival.endDate)}{festival.locationName ? " · " + festival.locationName : ""}</p>
        {festival.distanceMeters !== null && <p className={styles.distance}>
          <strong>가게에서 {festival.distanceMeters.toLocaleString("ko-KR")}m</strong>
          {/* ponytail: 도보 분속 67m 가정. 경로 기반 시간이 필요하면 지도 API로 교체 */}
          <span>도보 {Math.max(1, Math.round(festival.distanceMeters / 67))}분 거리</span>
        </p>}
      </> : item.basis && <p className={styles.condition}>{item.basis}</p>}
    {item.conditionType === "PRICE_SPIKE" && <PriceRows prices={report.ingredientPrices} />}
    <p className={styles.body}>{item.text}</p>
    <p className={styles.action}>→ {actionOf(item.title)}</p>
    <button type="button" className={styles.evidenceButton} onClick={() => sheet.current?.showModal()}>근거보기 ›</button>
    <dialog ref={sheet} className={styles.sheet} aria-label={label + " 처방 근거"}
      onClick={(e) => { if (e.target === e.currentTarget) e.currentTarget.close(); }}>
      <div className={styles.sheetHandle} aria-hidden="true" />
      <h2>{item.title}</h2>
      <p className={styles.sheetText}>{item.text}</p>
      <h3>이렇게 판단했어요</h3>
      <div className={styles.sheetBox}>
        <p>{item.basis ?? "이번 주 날씨·행사·상권 공공데이터를 바탕으로 정리했어요."}</p>
        {main && <p className={styles.sheetWhy}>참고 자료: {explain(main)}
          {main.url && <> <a href={main.url} target="_blank" rel="noreferrer">원문 보기 ↗</a></>}</p>}
      </div>
      <p className={styles.sheetNote}>운영 참고용 안내이며, 실제 매출을 예측하거나 보장하지 않습니다.</p>
      <form method="dialog"><button className={styles.sheetClose}>닫기</button></form>
    </dialog>
  </CardFrame>;
}
