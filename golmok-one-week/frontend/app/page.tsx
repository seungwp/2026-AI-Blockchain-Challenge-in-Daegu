import Link from "next/link";

export default function HomePage() {
  return (
    <main>
      <h1>골목 한 주</h1>
      <p>
        대구 골목상권 음식점 사장님을 위해 이번 주 날씨·행사·상권·메뉴 정보를 모아
        운영 참고용 점검 항목을 정리해 드립니다.
      </p>
      <p className="muted">
        매출을 예측하거나 보장하지 않습니다. 준비·점검이 필요한 부분을 알려드리는 서비스입니다.
      </p>
      <Link href="/search">
        <button type="button">가게 검색 시작</button>
      </Link>
    </main>
  );
}
