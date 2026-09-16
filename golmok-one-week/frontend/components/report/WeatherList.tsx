import type { WeatherDay } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";

export default function WeatherList({ days }: { days: WeatherDay[] }) {
  if (!days.length) return <p className="muted">날씨 정보를 불러오지 못했습니다.</p>;
  return (
    <section aria-label="7일 날씨">
      <div className="row">
        <h2>7일 날씨</h2>
        <DemoBadge show={days.some((d) => d.isDemoData)} />
      </div>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>날짜</th><th>요일</th><th>날씨</th><th>최고/최저</th><th>강수확률</th><th>강수량</th><th>습도</th><th>미세먼지</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date}>
                <td>{d.date}</td>
                <td>{d.dayOfWeek}</td>
                <td>{d.condition}</td>
                <td>{d.tempMax}℃ / {d.tempMin}℃</td>
                <td>{d.precipitationProbability}%</td>
                <td>{d.precipitationMm}mm</td>
                <td>{d.humidity == null ? "-" : `${d.humidity}%`}</td>
                <td>{d.pm10Grade}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">
        기온·강수·습도는 기상청 예보입니다. 미세먼지는 아직 예시 값입니다.
        4일 뒤부터는 동네 단위가 아닌 대구 전역 기준 중기예보라 습도가 제공되지 않습니다.
      </p>
    </section>
  );
}
