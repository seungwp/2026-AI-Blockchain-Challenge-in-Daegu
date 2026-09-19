import type { CommercialArea } from "@/types";
import type { Source } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";
import DataStatusNote from "./DataStatusNote";

export default function CommercialAreaSummary({ area, sources = [] }: { area: CommercialArea | null; sources?: Source[] }) {
  if (!area) return <p className="notice">가게 좌표가 확인되지 않아 주변 상권·행사 거리 분석을 제공하지 않습니다.</p>;
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
      </ul>
      <DataStatusNote items={[{
        label: "주변 상권", status: area.dataStatus, asOf: area.dataAsOf,
        source: sources.find((source) => area.sourceIds.includes(source.id)),
        note: "인허가·상가정보 수집본으로 집계한 값이며 실제 영업 현황과 차이가 있을 수 있어요.",
      }]} />
    </section>
  );
}
