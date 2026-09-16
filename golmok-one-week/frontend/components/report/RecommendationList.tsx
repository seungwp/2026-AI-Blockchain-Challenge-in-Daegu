import type { Recommendation } from "@/types";

/** 권고 목록. 각 항목은 근거(basis)와 출처 ID를 반드시 함께 보여준다. */
export default function RecommendationList({
  items,
  emptyText = "표시할 점검 항목이 없습니다.",
}: {
  items: Recommendation[];
  emptyText?: string;
}) {
  if (!items.length) return <p className="muted">{emptyText}</p>;
  return (
    <ul style={{ listStyle: "none", paddingLeft: 0 }}>
      {items.map((item, index) => (
        <li key={`${item.title}-${item.date ?? "week"}-${index}`} className="card">
          <div className="row">
            <strong>{item.title}</strong>
            <span className="badge">우선순위 {item.priority}</span>
            <span className="badge">신뢰도 {item.confidence}</span>
            {item.date && <span className="muted">{item.date}</span>}
          </div>
          <p>{item.text}</p>
          {item.basis && <p className="muted">근거: {item.basis}</p>}
          <p className="muted">출처 ID: {item.sourceIds.length ? item.sourceIds.join(", ") : "-"}</p>
        </li>
      ))}
    </ul>
  );
}
