import type { DataStatus, Source } from "@/types";
import styles from "./main-dashboard.module.css";

export type DataStatusItem = {
  label: string;
  status?: DataStatus | null;
  asOf?: string | null;
  source?: Source;
  note: string;
};

const statusLabel = (status?: DataStatus | null) => {
  if (status === "LIVE") return "실시간 조회";
  if (status === "SNAPSHOT") return "저장 데이터";
  if (status === "DEMO") return "예시 데이터";
  return "기준 정보 없음";
};

/** 근거보기에서만 데이터 성격·기준일·출처를 같은 형식으로 보여준다. */
export default function DataStatusNote({ items }: { items: DataStatusItem[] }) {
  if (!items.length) return null;
  return <section className={styles.dataStatus} aria-label="데이터 확인">
    <h3>데이터 확인</h3>
    {items.map((item) => <div className={styles.dataStatusRow} key={item.label}>
      <div><strong>{item.label}</strong><span className={styles[`status${item.status ?? "UNKNOWN"}`]}>{statusLabel(item.status)}</span></div>
      <p>{item.asOf ? `기준일 ${item.asOf}` : "기준일 정보 없음"} · {item.note}</p>
      {item.source && <p className={styles.dataSource}>출처: {item.source.title}
        {item.source.url && <> <a href={item.source.url} target="_blank" rel="noreferrer">원문 보기 ↗</a></>}
      </p>}
    </div>)}
  </section>;
}
