"use client";

import { useRef } from "react";
import type { CSSProperties, ReactNode } from "react";
import type { AnalysisReport, IngredientPrice, Recommendation, Source } from "@/types";
import styles from "./main-dashboard.module.css";
import DataStatusNote, { type DataStatusItem } from "./DataStatusNote";

const WEATHER = ["RAIN", "HOT", "COLD"];
function kind(item: Recommendation) {
  if (WEATHER.includes(item.conditionType)) return { label: "날씨", icon: "cloud" };
  if (item.conditionType === "FESTIVAL") return { label: "행사", icon: "calendar" };
  if (item.conditionType === "HOLIDAY") return { label: "명절", icon: "calendar" };
  if (item.conditionType === "PRICE_SPIKE") return { label: "식자재", icon: "cart" };
  if (item.conditionType === "COMPETITION") return { label: "주변 상권", icon: "competition" };
  if (item.type === "STAFFING") return { label: "인력", icon: "calendar" };
  return { label: item.type === "DELIVERY" ? "배달" : "운영", icon: "cloud" };
}
const monthDay = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  return `${d.getMonth() + 1}월 ${d.getDate()}일(${"일월화수목금토"[d.getDay()]})`;
};
const pct = (ratio: number) => (ratio >= 0 ? "+" : "") + Math.round(ratio * 100) + "%";
const shortDay = (iso: string) => {
  const d = new Date(iso + "T00:00:00");
  return `${d.getMonth() + 1}/${d.getDate()}`;
};
/** 출처 → "서울 음식점 카드매출과 … 분석한 연구 (한국자료분석학회, 2019)" — sources.csv 값만 조합 */
const explain = (s: Source) => `${s.description ?? s.title} (${[s.organization, s.publicationYear].filter(Boolean).join(", ") || s.title})`;
/** 수치나 효과를 덧붙이지 않는, 유형별 실행 안내. */
function nextStep(item: Recommendation) {
  const when = item.date ? `${monthDay(item.date)} 전` : "이번 주";
  const what = (() => {
    switch (item.conditionType) {
      case "RAIN": return "포장 용기 재고와 배달앱 영업 상태를 확인하세요.";
      case "HOT": return "냉방 상태와 재료 보관 온도를 확인하세요.";
      case "COLD": return "포장 보온 상태와 배달 지연 안내를 확인하세요.";
      case "WEEKEND": return "피크 시간대 인력과 조리 순서를 확인하세요.";
      case "HOLIDAY": return "영업 여부를 확정하고 발주와 인력 배치를 확인하세요.";
      case "FESTIVAL": return "교통·주차 안내와 매장 동선을 확인하세요.";
      case "COMPETITION": return "대표 메뉴와 포장·응대 안내를 점검하세요.";
      case "PRICE_SPIKE": return "발주 시점과 대체 매입처를 확인하세요.";
      default:
        return item.type === "STAFFING" ? "인력 배치를 확인하세요."
          : item.type === "DELIVERY" ? "포장·배달 상태를 확인하세요."
            : item.type === "MENU" ? "메뉴 구성을 확인하세요."
              : "운영 준비 사항을 확인하세요.";
    }
  })();
  return `→ ${when} ${what}`;
}

/** 카드에는 실행할 일만, 연구·모델·출처 설명은 근거보기에서만 보여준다. */
function actionText(text: string) {
  return text
    .replace(/서울 3개 구 음식점 카드매출 분석에서 /g, "")
    .replace(/(?:비 예보일에는 배달 주문이 늘어난다는|아주 더운 날에도 배달 주문이 늘어난다는|기온이 크게 떨어지는 날은 배달 주문이 늘어난다는|배달 주문은 평일보다 금·토·일에 많다는) 분석이 있습니다(?:\([^)]*\))?\.\s*/g, "")
    .replace(/비 오는 날 배달 주문이 크게 늘었다는 배달앱 분석이 있습니다\.\s*/g, "")
    .replace(/비 예보일에는 치킨 배달 주문이 늘어난다는 분석이 있습니다\.\s*/g, "")
    .replace(/매출이 가장 높은 요일은 매장 규모에 따라 다르다는 분석이 있습니다(?:\([^)]*\))?\.\s*/g, "")
    .replace(/ 다만 음식점이 많은 지역일수록 매출도 함께 증가한다는 분석이 있어, 경쟁점 수만으로 상권을 낮게 평가하지는 않습니다\./g, "")
    .replace(
      /^주요 식자재\(([^)]+)\) 중 향후 가격이 오를 가능성이 모델에서 높게 나온 품목이 있습니다\..*?(발주 시점과 대체 매입처를 확인해보는 참고 자료로 활용하는 것을 권장합니다\.)$/,
      "주요 식자재($1)의 가격 변동 가능성을 확인해보세요. $2",
    );
}

/** 기존 저장 리포트의 상권 등급 표기도 현재 화면 정책에 맞춰 숨긴다. */
const displayBasis = (basis: string, item: Recommendation) => item.conditionType === "COMPETITION"
  ? basis.replace(/\s*\([^)]*\)/g, "")
  : basis;

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
        <div>
          <span>{p.item} {p.unit ?? ""}</span>
          {change && <small className={(ratio ?? p.vsNormalRatio ?? 0) >= 0.1 ? styles.up : undefined}>{change}</small>}
          {/* 급등 확률: 예측 모델 결과(다음 7일 10% 이상 상승 가능성). 예측이 오래되면 백엔드가 null로 내려준다. */}
          {typeof p.probSpike === "number" && <small className={p.alert ? styles.spikeAlert : styles.spike}>
            다음 7일 급등 확률 {Math.round(p.probSpike * 100)}%{p.predictionDate ? ` · ${shortDay(p.predictionDate)} 예측` : ""}
          </small>}
        </div>
        <strong>{p.price !== null ? p.price.toLocaleString("ko-KR") + "원" : "-"}</strong>
        <PriceTrend item={p} />
      </div>;
    })}
    {first && <p className={styles.priceNote}>{first.priceBasis ?? "KAMIS 대구 소매가 기준"}{first.priceDate && !first.isDemoData ? " · " + monthDay(first.priceDate) : ""}</p>}
  </>;
}

/** KAMIS 관측값만 사용한 최근 7일 추이. 휴장일은 점을 억지로 보간하지 않는다. */
function PriceTrend({ item }: { item: IngredientPrice }) {
  if (!item.priceDate || !item.history?.length) return null;
  const cutoff = new Date(item.priceDate + "T00:00:00");
  cutoff.setDate(cutoff.getDate() - 6);
  const points = item.history
    .filter((point) => point.price > 0 && new Date(point.date + "T00:00:00") >= cutoff)
    .sort((a, b) => a.date.localeCompare(b.date));
  if (points.length < 2) return null;
  const values = points.map((point) => point.price);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const first = points[0];
  const last = points.at(-1)!;
  const start = new Date(first.date + "T00:00:00").getTime();
  const end = new Date(last.date + "T00:00:00").getTime();
  const xOf = (date: string) => end === start ? 164 : 32 + ((new Date(date + "T00:00:00").getTime() - start) / (end - start)) * 264;
  const yOf = (price: number) => 52 - ((price - min) / range) * 40;
  const polyline = points.map((point) => {
    const x = xOf(point.date);
    const y = yOf(point.price);
    return `${x},${y}`;
  }).join(" ");
  const labelPrice = (price: number) => `${Math.round(price).toLocaleString("ko-KR")}원`;
  return <div className={styles.priceTrend}>
    <div className={styles.priceTrendTop}><span>최근 7일 추이</span><span>{shortDay(first.date)} · {shortDay(last.date)}</span></div>
    <svg viewBox="0 0 300 74" role="img" aria-label={`${item.item} 최근 7일 가격 변동 그래프: 최저 ${labelPrice(min)}, 최고 ${labelPrice(max)}`}>
      <line className={styles.chartGrid} x1="32" x2="296" y1="12" y2="12" />
      <line className={styles.chartGrid} x1="32" x2="296" y1="52" y2="52" />
      <text x="0" y="15">{labelPrice(max)}</text>
      <text x="0" y="55">{labelPrice(min)}</text>
      <polyline points={polyline} />
      {points.map((point) => <g key={point.date}>
        <circle cx={xOf(point.date)} cy={yOf(point.price)} r="3" />
        <title>{`${shortDay(point.date)} ${labelPrice(point.price)}`}</title>
      </g>)}
      <text x={xOf(first.date)} y="70" textAnchor="start">{shortDay(first.date)}</text>
      <text x={xOf(last.date)} y="70" textAnchor="end">{shortDay(last.date)}</text>
    </svg>
  </div>;
}

/** 처방이 없어도 가격 정보가 있으면 보여주는 식자재 참고 카드. */
export function IngredientCard({ prices, sources }: { prices: IngredientPrice[]; sources: Source[] }) {
  const sheet = useRef<HTMLDialogElement>(null);
  if (!prices.length) return null;
  return <CardFrame label="식자재" icon="cart" badge={<span className={`${styles.badge} ${styles.badgeMuted}`}>참고</span>}>
    <PriceRows prices={prices} />
    <button type="button" className={styles.evidenceButton} onClick={() => sheet.current?.showModal()}>데이터 확인 ›</button>
    <dialog ref={sheet} className={styles.sheet} aria-label="식자재 가격 데이터 확인"
      onClick={(e) => { if (e.target === e.currentTarget) e.currentTarget.close(); }}>
      <div className={styles.sheetHandle} aria-hidden="true" />
      <h2>식자재 가격 데이터 확인</h2>
      <p className={styles.sheetText}>급등 확률은 KAMIS 대구 소매가와 대구 날씨 기록으로 학습한 예측 모델의 결과로,
        <strong> 다음 7일 평균가가 지난 7일보다 10% 이상 오를 가능성</strong>을 뜻합니다.
        2026년 실제 가격으로 검증했으며(같은 기간 단순 규칙보다 놓치는 급등이 적음), 예측일로부터 7일이 지나면 표시하지 않습니다.</p>
      <DataStatusNote items={prices.map((price) => ({
        label: `${price.item} ${price.unit ?? ""}`.trim(), status: price.dataStatus, asOf: price.dataAsOf ?? price.priceDate,
        source: sources.find((source) => source.id === price.sourceId),
        note: price.dataStatus === "LIVE" ? "KAMIS에서 조회한 대구 소매 참고가격입니다. 실제 매입가와 다를 수 있어요."
          : price.dataStatus === "SNAPSHOT" ? "저장된 KAMIS 수집값입니다. 실제 매입가와 다를 수 있어요."
            : "예시 가격입니다. 실제 운영 판단에는 사용하지 마세요.",
      }))} />
      <form method="dialog"><button className={styles.sheetClose}>닫기</button></form>
    </dialog>
  </CardFrame>;
}

/** 날씨 처방 규칙이 발동하지 않은 주에도 실제 예보 자체는 홈에서 확인할 수 있게 한다. */
function WeatherCard({ report }: { report: AnalysisReport }) {
  const days = report.weather.filter((day) => !day.isDemoData);
  const day = days.reduce<typeof days[number] | null>((picked, current) => {
    if (!picked) return current;
    return (current.precipitationProbability ?? -1) > (picked.precipitationProbability ?? -1) ? current : picked;
  }, null);
  if (!day) return null;
  return <CardFrame label="날씨" icon="cloud" badge={<span className={`${styles.badge} ${styles.badgeMuted}`}>예보</span>}>
    <p className={styles.condition}>{monthDay(day.date)} · {day.condition} · 최고 {day.tempMax ?? "-"}° / 최저 {day.tempMin ?? "-"}° · 강수확률 {day.precipitationProbability ?? "-"}%</p>
    <p className={styles.body}>기상청 예보를 기준으로 이번 주 날씨를 확인했어요.</p>
    <p className={styles.action}>→ 영업 전 날씨와 매장 준비 상태를 확인하세요.</p>
  </CardFrame>;
}

/** 행사가 없다는 것도 조회 결과임을 알려주되, 처방보다 눈에 띄지 않게 표시한다. */
function FestivalEmptyCard() {
  return <CardFrame label="행사" icon="calendar" badge={<span className={`${styles.badge} ${styles.badgeMuted}`}>주변 행사 없음</span>}>
    <p className={styles.body}>주변 3km 내 예정된 축제 정보가 없어요.</p>
    <p className={styles.priceNote}>TourAPI 수집 기준 · 행사 일정은 변경될 수 있어요.</p>
  </CardFrame>;
}

/** 홈·지난 주 상세 공통 카드 목록. 식자재 처방이 없으면 가격 참고 카드를 끝에 붙인다. */
export function ActionList({ report }: { report: AnalysisReport }) {
  return <>
    {!report.topActions.length && <p className={styles.empty}>이번 주 특별히 강조할 점검 항목이 없어요.</p>}
    {!report.topActions.some((item) => WEATHER.includes(item.conditionType)) && <WeatherCard report={report} />}
    {report.topActions.map((item, i) => <ActionCard key={item.title + i} item={item} report={report} />)}
    {!report.isDemoData && !report.festivals.length && <FestivalEmptyCard />}
    {!report.topActions.some((a) => a.conditionType === "PRICE_SPIKE") && <IngredientCard prices={report.ingredientPrices} sources={report.sources} />}
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
  // 처방 근거에 적힌 행사만 연결한다. 관련 행사가 없으면 첫 번째 행사를 임의로 표시하지 않는다.
  const festival = label === "행사"
    ? report.festivals.find((event) => item.basis?.includes(event.name))
    : undefined;
  const dataItems: DataStatusItem[] = day ? [{
    label: "날씨 예보", status: day.dataStatus, asOf: day.dataAsOf,
    source: report.sources.find((source) => source.id === day.sourceId),
    note: day.dataStatus === "LIVE" ? "기상청 예보는 변경될 수 있어요."
      : "예보 정보를 불러오지 못해 예시 값이 포함되어 있어요.",
  }] : festival ? [{
    label: "행사 정보", status: festival.dataStatus, asOf: festival.dataAsOf ?? festival.fetchedAt,
    source: report.sources.find((source) => source.id === festival.sourceId),
    note: festival.dataStatus === "SNAPSHOT" ? "TourAPI에서 수집한 정보이며 주최 측 사정으로 일정·장소가 바뀔 수 있어요."
      : "예시 행사 정보입니다.",
  }] : item.conditionType === "PRICE_SPIKE" ? report.ingredientPrices.map((price) => ({
    label: `${price.item} ${price.unit ?? ""}`.trim(), status: price.dataStatus, asOf: price.dataAsOf ?? price.priceDate,
    source: report.sources.find((source) => source.id === price.sourceId),
    note: price.dataStatus === "LIVE" ? "KAMIS에서 조회한 대구 소매 참고가격입니다. 실제 매입가와 다를 수 있어요."
      : price.dataStatus === "SNAPSHOT" ? "저장된 KAMIS 수집값입니다. 실제 매입가와 다를 수 있어요."
        : "예시 가격입니다.",
  })) : item.conditionType === "COMPETITION" && report.commercialArea ? [{
    label: "주변 상권", status: report.commercialArea.dataStatus, asOf: report.commercialArea.dataAsOf,
    source: report.sources.find((source) => report.commercialArea?.sourceIds.includes(source.id)),
    note: "인허가·상가정보 수집본으로 집계한 값이며 실제 영업 현황과 차이가 있을 수 있어요.",
  }] : [];

  return <CardFrame label={label} icon={icon} badge={<Priority item={item} />}>
    {day ? <p className={styles.condition}>{day.condition} · 최고 {day.tempMax ?? "-"}° / 최저 {day.tempMin ?? "-"}° · 강수확률 {day.precipitationProbability ?? "-"}%</p>
      : festival ? <>
        <p className={styles.condition}>{festival.name} · {monthDay(festival.startDate)} ~ {monthDay(festival.endDate)}{festival.locationName ? " · " + festival.locationName : ""}</p>
        {festival.distanceMeters !== null && <p className={styles.distance}>
          <strong>가게와 직선거리 약 {festival.distanceMeters.toLocaleString("ko-KR")}m</strong>
        </p>}
      </> : item.basis && <p className={styles.condition}>{displayBasis(item.basis, item)}</p>}
    {item.conditionType === "PRICE_SPIKE" && <PriceRows prices={report.ingredientPrices} />}
    <p className={styles.body}>{actionText(item.text)}</p>
    <p className={styles.action}>{nextStep(item)}</p>
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
      <DataStatusNote items={dataItems} />
      <p className={styles.sheetNote}>운영 참고용 안내이며, 실제 매출을 예측하거나 보장하지 않습니다.</p>
      <form method="dialog"><button className={styles.sheetClose}>닫기</button></form>
    </dialog>
  </CardFrame>;
}
