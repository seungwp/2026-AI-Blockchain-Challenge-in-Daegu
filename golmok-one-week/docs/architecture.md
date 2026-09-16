# 아키텍처

```
Browser
  ↓
Next.js Frontend (App Router, TypeScript)
  ↓ REST API (JSON, CORS 허용: http://localhost:3000)
Spring Boot Backend (Java 21)
  ├── StoreSearchProvider     (Mock → 소상공인 상가정보 API로 교체 가능)
  ├── WeatherProvider         (Mock → 기상청 단기예보 API)
  ├── AirQualityProvider      (Mock → 에어코리아)
  ├── FestivalProvider        (Mock/H2 → 대구 축제 API)
  ├── CommercialAreaService   (반경 500m 집계)
  ├── WeeklyGuideRuleEngine   (날씨·행사·상권·메뉴 규칙 → 권고)
  └── H2 Database (MySQL 전환 가능)
```

## 책임 분리

**Frontend**
- 사용자 입력, API 요청, 데이터 표시, 페이지 전환
- 계산·판단 로직 없음 (권고 생성은 전부 백엔드)
- 백엔드 호출 실패 시 `lib/mock.ts` 데모 데이터로 폴백

**Backend**
- 가게 검색, 메뉴 카테고리 분류, 날씨·행사·상권 데이터 통합
- 운영 가이드 규칙 적용, 리포트 저장, 출처 연결

## 리포트 생성 흐름

1. `POST /api/reports` 수신 (storeId, mainMenu, menuCategory)
2. 가게 조회 → menuCategory 미지정 시 자동 분류
3. `WeatherProvider` + `AirQualityProvider` 로 7일 예보 병합
4. `FestivalProvider` 로 기간 중 행사 조회 → 가게와의 거리 계산 → 영향 구분(1km/3km)
5. `CommercialAreaService` 로 반경 500m 상권 집계
6. `WeeklyGuideRuleEngine` 이 menuCategory + COMMON 규칙을 날짜별로 평가
7. 결과를 `AnalysisReport`에 JSON으로 저장 후 응답 (reportId로 재조회 가능)

## 규칙 엔진 설계

- `MenuRule` 테이블에 (메뉴 카테고리, 조건, 조건값, 권고 유형, 문구, 출처, 신뢰도)를 저장
- `menuCategory = COMMON` 인 규칙은 모든 메뉴에 적용
- 조건 평가
  - `RAIN`: 강수확률 ≥ 조건값(%)
  - `HOT`: 최고기온 ≥ 조건값(℃)
  - `COLD`: 최저기온 ≤ 조건값(℃)
  - `DUST`: 미세먼지 나쁨/매우나쁨
  - `WEEKEND`: 토·일
  - `FESTIVAL`: 행사장 거리 ≤ 조건값(m)
  - `COMPETITION`: 반경 500m 유사 업종 수 ≥ 조건값 (주간 전체 권고)
- 우선순위: 규칙 신뢰도 + 조건 강도(강수확률 60% 이상, 33℃ 이상, 1km 이내 행사 등)로 결정
- Top 3: 우선순위 → 가까운 날짜 순, **같은 (조건, 권고 유형) 조합은 한 번만** 담아 종류가 겹치지 않게 구성

## 매출 관련 원칙

- 매출액·증감률을 계산하지 않습니다. 실제 매출 데이터가 없기 때문입니다.
- 모든 문구는 "점검 권장 / 준비 권장 / 고려" 수준이며, 각 권고는 `sourceIds`로 근거를 연결합니다.
- 단일 논문 결과를 모든 메뉴로 일반화하지 않습니다 (치킨 논문 → 치킨 규칙에만 연결).
