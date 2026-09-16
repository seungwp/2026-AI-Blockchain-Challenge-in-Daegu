# 골목 한 주

대구 골목상권 음식점 사장님이 **상호명 또는 주소**와 **대표 메뉴**를 입력하면,
주변 상권·이번 주 날씨·대구 축제/행사·메뉴 특성을 결합해 **이번 주 운영 가이드**를 제공하는 웹서비스입니다.

> 본 결과는 공공데이터 및 연구자료를 기반으로 한 **운영 참고용** 제안이며, 실제 매출을 보장하지 않습니다.
> 매출액·매출 증감률을 예측하지 않습니다.

이 저장소는 **디자인팀에 넘길 개발용 MVP**입니다. 디자인·브랜딩·애니메이션은 의도적으로 제외했습니다.

## 구성

```
golmok-one-week/
├── frontend/   Next.js(App Router) + TypeScript  : 입력·표시·페이지 전환
├── backend/    Spring Boot 3.5 + Java 21 + H2    : 검색·분류·데이터 통합·규칙·저장
└── docs/       아키텍처 / API / 데이터·논문 출처 / 디자인 인수인계
```

## 실행 방법

### 1) 백엔드 (먼저 실행)
```bash
cd backend
./gradlew bootRun          # Windows: gradlew.bat bootRun
```
- 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:golmok`, 사용자 `sa`, 비밀번호 없음)
- 첫 기동 시 데모 데이터(가게 8곳, 행사 3건, 출처 6건, 규칙 28건)가 자동 입력됩니다.

### 2) 프론트엔드
```bash
cd frontend
cp .env.local.example .env.local   # 필요 시 API 주소 수정
npm install
npm run dev                        # http://localhost:3000
```

### 요구 환경
- Java 21 이상, Node.js 20 이상
- 외부 API 키 없이 동작합니다 (모든 외부 데이터는 Mock Provider)

## 데모 시나리오

1. `/` 에서 **가게 검색 시작**
2. `/search` 에서 `두류공원` 검색 → **두류공원 치킨** 선택
3. `/store/5/confirm` 에서 대표 메뉴 `치킨` 입력 → 자동 분류(치킨) 확인 → **이번 주 분석하기**
4. `/report/{id}` 에서 Top 3 행동, 7일 날씨, 상권, 행사(치맥페스티벌 데모), 요일별 가이드, 출처 확인
5. 새로고침해도 같은 `reportId` 로 동일한 결과가 표시됩니다.

검색 결과가 없을 때는 주소를 직접 입력해 진행할 수 있습니다.
백엔드를 끄고 프론트만 실행하면 `lib/mock.ts` 의 데모 데이터로 전체 흐름이 그대로 동작합니다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 시스템 구조, 리포트 생성 흐름, 규칙 엔진 설계 |
| [docs/api-spec.md](docs/api-spec.md) | REST API 명세와 에러 형식 |
| [docs/data-sources.md](docs/data-sources.md) | 공공데이터 출처와 Provider 교체 방법 |
| [docs/coefficients.md](docs/coefficients.md) | 연구자료 출처·계수 근거와 활용 원칙 |
| [docs/handoff-to-design.md](docs/handoff-to-design.md) | **디자인팀 인수인계** (페이지·컴포넌트·타입·상태·주의사항) |
