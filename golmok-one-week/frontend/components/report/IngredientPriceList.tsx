import type { IngredientPrice } from "@/types";
import DemoBadge from "@/components/common/DemoBadge";

export default function IngredientPriceList({ prices }: { prices: IngredientPrice[] }) {
  return (
    <section aria-label="식자재 참고 가격">
      <h2>식자재 참고 가격</h2>
      {!prices.length && (
        <p className="muted">이 메뉴 카테고리에 연결된 식자재 품목이 없습니다.</p>
      )}
      {prices.length > 0 && (
        <>
          <ul style={{ listStyle: "none", paddingLeft: 0 }}>
            {prices.map((p) => (
              <li key={p.item} className="card">
                <div className="row">
                  <strong>{p.item}</strong>
                  <DemoBadge show={p.isDemoData} />
                  {p.alert && <span className="badge">확인 필요</span>}
                </div>
                <p>
                  {p.price != null
                    ? `${p.unit ?? ""} ${p.price.toLocaleString()}원`.trim()
                    : "가격 정보 없음"}
                  {p.priceDate ? ` (${p.priceDate} 기준)` : ""}
                </p>
                <p className="muted">
                  평년 대비{" "}
                  {p.vsNormalRatio != null
                    ? `${p.vsNormalRatio >= 0 ? "+" : ""}${Math.round(p.vsNormalRatio * 100)}%`
                    : "-"}
                  {p.alert && p.probSpike != null &&
                    ` · 향후 7일 내 급등 가능성 모델 추정치 ${Math.round(p.probSpike * 100)}%`}
                </p>
                <p className="muted">출처 ID: {p.sourceId ?? "-"}</p>
              </li>
            ))}
          </ul>
          <p className="muted">
            급등확률은 참고용 모델 추정치이며(정밀도 약 20%), 알림이 떠도 실제로는 오르지 않는 경우가
            많습니다. 확정 신호가 아닌 확인 참고용으로만 활용해주세요.
          </p>
        </>
      )}
    </section>
  );
}
