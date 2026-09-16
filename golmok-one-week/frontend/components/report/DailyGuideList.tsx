import type { DailyGuide } from "@/types";
import RecommendationList from "./RecommendationList";

export default function DailyGuideList({ guides }: { guides: DailyGuide[] }) {
  return (
    <section aria-label="요일별 운영 가이드">
      <h2>요일별 운영 가이드</h2>
      {guides.map((day) => (
        <div key={day.date} className="card">
          <h3>{day.date} ({day.dayOfWeek})</h3>
          <p className="muted">{day.weatherSummary}</p>
          <RecommendationList items={day.guides} emptyText="이 날은 특이 조건이 적어 평소 운영 기준으로 준비하셔도 됩니다." />
        </div>
      ))}
    </section>
  );
}
