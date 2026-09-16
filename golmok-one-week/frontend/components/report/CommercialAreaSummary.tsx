import type { CommercialArea } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";

export default function CommercialAreaSummary({ area }: { area: CommercialArea | null }) {
  if (!area) return null;
  return (
    <section aria-label="주변 생활권 요약">
      <div className="row">
        <h2>주변 생활권</h2>
        <DemoBadge show={area.isDemoData} />
      </div>
      <ul>
        <li>지역: {area.district ?? "-"} {area.dong ?? ""}</li>
        <li>반경 500m 음식점 수: {area.totalStores ?? "-"}곳</li>
        <li>유사 업종 수: {area.sameCategoryStores ?? "-"}곳</li>
        <li>경쟁 강도: {area.competitionLevel ?? "-"}</li>
      </ul>
      {area.note && <p className="muted">{area.note}</p>}
      <p className="muted">
        음식점 수가 많을수록 해당 지역 음식점 매출도 함께 증가한다는 분석이 있습니다
        (한국자료분석학회지 21(1), 2019, 서울 3개 구 카드매출 기준).
        유사 업종 수가 많다는 사실만으로 상권을 낮게 평가하지 않습니다.
      </p>
      <p className="muted">출처 ID: {area.sourceIds.join(", ") || "-"}</p>
    </section>
  );
}
