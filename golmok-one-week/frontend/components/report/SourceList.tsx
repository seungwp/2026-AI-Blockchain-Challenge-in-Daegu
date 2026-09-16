import type { Source } from "@/types";

/** 추천의 근거가 된 출처 목록. sourceIds 연결과 함께 유지한다. */
export default function SourceList({ sources }: { sources: Source[] }) {
  return (
    <section aria-label="출처">
      <h2>근거 출처</h2>
      {!sources.length && <p className="muted">표시할 출처가 없습니다.</p>}
      <ul style={{ listStyle: "none", paddingLeft: 0 }}>
        {sources.map((s) => (
          <li key={s.id} className="card">
            <div className="row">
              <span className="badge">ID {s.id}</span>
              <span className="badge">{s.sourceType}</span>
            </div>
            <p><strong>{s.title}</strong></p>
            <p className="muted">
              {[s.organization, s.publicationYear].filter(Boolean).join(" · ")}
            </p>
            {s.description && <p>{s.description}</p>}
            {s.reliabilityNote && <p className="muted">해석 시 유의: {s.reliabilityNote}</p>}
            {s.url && (
              <p><a href={s.url} target="_blank" rel="noreferrer">{s.url}</a></p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
