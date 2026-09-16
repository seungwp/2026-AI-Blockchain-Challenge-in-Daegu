import type { FestivalEvent } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";

export default function FestivalList({ festivals }: { festivals: FestivalEvent[] }) {
  return (
    <section aria-label="축제·행사">
      <h2>축제 / 행사</h2>
      {!festivals.length && <p className="muted">분석 기간에 확인된 인근 행사가 없습니다.</p>}
      <ul style={{ listStyle: "none", paddingLeft: 0 }}>
        {festivals.map((f) => (
          <li key={f.id} className="card">
            <div className="row">
              <strong>{f.name}</strong>
              <DemoBadge show={f.isDemoData} />
              {f.impactLevel && <span className="badge">{f.impactLevel}</span>}
            </div>
            <p>{f.startDate} ~ {f.endDate} · {f.locationName ?? ""}</p>
            <p className="muted">
              가게에서 거리: {f.distanceMeters != null ? `${f.distanceMeters}m` : "거리 정보 없음"}
            </p>
            {f.impactNote && <p className="muted">{f.impactNote}</p>}
            <p className="muted">출처 ID: {f.sourceId ?? "-"}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
