"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import StateMessage from "@/components/common/StateMessage";
import { getReport } from "@/lib/api";
import { latestReportByStore, removeStoreHistory, useReport } from "@/lib/history";
import { saveDraft, selectStore } from "@/lib/onboarding";
import styles from "@/components/report/main-dashboard.module.css";

const ALARM_KEY = "golmok:alarm-day:v1";
const ALARM_EVENT = "golmok:alarm-day-change";
const readAlarmDay = () => {
  try { return localStorage.getItem(ALARM_KEY) ?? "월요일"; } catch { return "월요일"; }
};
const subscribeAlarmDay = (listener: () => void) => {
  const onStorage = (event: StorageEvent) => { if (event.key === ALARM_KEY) listener(); };
  window.addEventListener("storage", onStorage);
  window.addEventListener(ALARM_EVENT, listener);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(ALARM_EVENT, listener);
  };
};

/** 내 페이지 (Figma 1차 73:499) */
export default function MyPage() {
  const reportId = Number(useParams<{ reportId: string }>().reportId);
  const router = useRouter();
  const { state, report } = useReport(reportId);
  const [open, setOpen] = useState<"" | "pos" | "alarm" | "stores" | "about">("");
  const [storeVersion, setStoreVersion] = useState(0);
  const [editingStoreId, setEditingStoreId] = useState<number | null>(null);
  const alarmDay = useSyncExternalStore(subscribeAlarmDay, readAlarmDay, () => "월요일");
  const [alarmDraftDay, setAlarmDraftDay] = useState("월요일");
  const [alarmSaved, setAlarmSaved] = useState(false);
  const days = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"];
  const toggle = (key: typeof open) => setOpen((v) => (v === key ? "" : key));
  function openAlarmSettings() {
    setAlarmSaved(false);
    setAlarmDraftDay(alarmDay);
    setOpen((value) => value === "alarm" ? "" : "alarm");
  }
  function saveAlarmDay() {
    try {
      localStorage.setItem(ALARM_KEY, alarmDraftDay);
      window.dispatchEvent(new Event(ALARM_EVENT));
    } catch { /* 저장 불가 환경에서는 다음 방문까지 유지되지 않는다. */ }
    setOpen("");
    setAlarmSaved(true);
  }
  if (!report) return <main className={styles.main}><StateMessage state={state} message={state === "error" ? "가게 정보를 불러오지 못했습니다." : undefined} /></main>;

  const { store, commercialArea: area } = report;
  const stores = latestReportByStore();
  const storeCount = stores.length || 1;

  async function editStore(targetReportId: number) {
    if (editingStoreId !== null) return;
    setEditingStoreId(targetReportId);
    try {
      const target = await getReport(targetReportId);
      selectStore(target.store);
      saveDraft({ mainMenu: target.mainMenu, menuCategory: target.menuCategory, classifiedMenu: target.mainMenu });
      router.push(`/store/${target.store.id}/confirm`);
    } finally {
      setEditingStoreId(null);
    }
  }

  function deleteStore(storeId: number, storeName: string) {
    if (!window.confirm(`‘${storeName}’ 가게와 이 기기에 저장된 처방 이력을 삭제할까요?`)) return;
    const remaining = removeStoreHistory(storeId);
    setStoreVersion((value) => value + 1);
    if (storeId !== store.id) return;
    const next = remaining[0];
    if (next) router.replace(`/report/${next.reportId}/my`);
    else {
      saveDraft({ keyword: "", store: null, mainMenu: "", menuCategory: "기타", classifiedMenu: "", classification: null });
      router.replace("/search");
    }
  }
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
        주변 생활권 분석<span>{area ? `반경 500m · 음식점 ${area.totalStores ?? "-"}곳 · 유사 업종 ${area.sameCategoryStores ?? "-"}곳` : "위치 정보 없음"} ›</span>
      </Link>
    </section>
    <section className={styles.card} aria-labelledby="pos-title">
      <div className={styles.cardTop}><h2 id="pos-title" className={styles.cardTitle}>가게데이터 연결</h2><span className={`${styles.badge} ${styles.badgeMuted}`}>연결 전</span></div>
      <p className={styles.body}>카드매출·포스·배달앱 주문을 연결하면 동네 평균이 아니라 우리 가게 기준으로 알려드려요.</p>
      <button type="button" className={styles.textButton} aria-expanded={open === "pos"} onClick={() => toggle("pos")}>연결하기 ›</button>
      {open === "pos" && <p className={styles.notice} role="status">가게 데이터 연결은 준비 중이에요. 지금은 공공데이터로 처방을 받을 수 있어요.</p>}
    </section>
    <section className={styles.settings} aria-label="설정">
      <button type="button" className={styles.settingRow} aria-expanded={open === "alarm"} onClick={openAlarmSettings}>알림 설정<span>{alarmDay} ›</span></button>
      {open === "alarm" && <div className={styles.alarmSettings}>
        <p>처방 알림을 받을 요일을 선택해주세요.</p>
        <div role="radiogroup" aria-label="처방 알림 요일">
          {days.map((day) => <button type="button" role="radio" aria-checked={alarmDraftDay === day}
            className={alarmDraftDay === day ? styles.selectedDay : undefined} key={day} onClick={() => setAlarmDraftDay(day)}>{day.slice(0, 1)}</button>)}
        </div>
        <button type="button" className={styles.saveAlarm} onClick={saveAlarmDay}>알림 설정 저장</button>
      </div>}
      {alarmSaved && <p className={styles.savedNotice} role="status">알림 요일을 {alarmDay}로 저장했어요.</p>}
      <button type="button" className={styles.settingRow} aria-expanded={open === "stores"} onClick={() => toggle("stores")}>가게 추가·전환<span>가게 {storeCount}곳 ›</span></button>
      {open === "stores" && <div className={styles.storeManager} key={storeVersion}>
        {stores.map((entry) => <div className={styles.managedStore} key={entry.storeId}>
          <Link href={`/report/${entry.reportId}`} className={entry.storeId === store.id ? styles.currentStore : undefined}>
            <strong>{entry.storeName}</strong><small>{entry.storeId === store.id ? "현재 가게" : "가게 전환"}</small>
          </Link>
          <div>
            <button type="button" disabled={editingStoreId !== null} onClick={() => void editStore(entry.reportId)}>
              {editingStoreId === entry.reportId ? "불러오는 중" : "수정"}
            </button>
            <button type="button" className={styles.deleteButton} onClick={() => deleteStore(entry.storeId, entry.storeName)}>삭제</button>
          </div>
        </div>)}
        <Link href="/search" className={styles.addStore}>+ 새 가게 추가</Link>
        <p>삭제하면 이 브라우저의 처방 이력에서 제거돼요. 공공데이터 원본은 삭제되지 않아요.</p>
      </div>}
      <button type="button" className={styles.settingRow} aria-expanded={open === "about"} onClick={() => toggle("about")}>서비스 안내<span>›</span></button>
      {open === "about" && <p className={styles.notice}>{report.disclaimer} 날씨·축제·식자재 가격·상권 공공데이터를 근거로 하며, 매출을 예측하지 않습니다.</p>}
    </section>
    <p className={styles.endNote}>장사메이트 · 운영 참고용 서비스 · 매출을 보장하지 않아요</p>
  </main>;
}
