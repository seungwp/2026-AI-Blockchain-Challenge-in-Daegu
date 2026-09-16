import type { AnalysisReport } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";
import RecommendationList from "./RecommendationList";

export default function ReportSummary({ report }: { report: AnalysisReport }) {
  return (
    <section aria-label="리포트 요약">
      <div className="row">
        <h1>{report.store.name} · 이번 주 운영 가이드</h1>
        <DemoBadge show={report.isDemoData} />
      </div>
      <p className="muted">
        {report.store.category} · {report.store.address}
      </p>
      <p>
        대표 메뉴: <strong>{report.mainMenu}</strong> (카테고리: {report.menuCategory})
      </p>
      <p>
        분석 기간: {report.analysisStartDate} ~ {report.analysisEndDate}
      </p>
      <p>{report.summary}</p>
      {report.demoNotice && <p className="notice">{report.demoNotice}</p>}

      <h2>이번 주 핵심 행동 Top 3</h2>
      <RecommendationList items={report.topActions} emptyText="이번 주 특별히 강조할 점검 항목이 없습니다." />
    </section>
  );
}
