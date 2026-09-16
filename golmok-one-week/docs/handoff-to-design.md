# 디자인팀 인수인계 문서

현재 코드는 **기능 검증용 MVP**입니다. 디자인 요소(색상 테마, 아이콘, 애니메이션, 카드 스타일)는 의도적으로 넣지 않았습니다.
UI/UX를 전면 교체해도 비즈니스 로직이 깨지지 않도록 API 타입과 컴포넌트 책임을 분리해 두었습니다.

## 1. 페이지별 목적

| 경로 | 목적 |
|---|---|
| `/` | 서비스 소개 한 줄 + 검색 시작 진입점 |
| `/search` | 상호명·주소로 가게를 찾고 선택. 결과가 없으면 주소 직접 입력 |
| `/store/[storeId]/confirm` | 선택한 가게 확인, 대표 메뉴 입력, 메뉴 카테고리 자동 분류 결과 확인·수정, 분석 실행 |
| `/report/[reportId]` | 이번 주 운영 가이드 결과. 새로고침해도 `reportId` 기준으로 동일한 결과 |

## 2. 페이지별 필수 UI 요소

### `/`
- 서비스명(골목 한 주), 한 줄 설명
- "가게 검색 시작" 버튼
- 매출 예측이 아니라는 안내 문구

### `/search`
- 상호명/주소 입력 필드 + 검색 버튼
- 검색 결과 목록: 가게명 / 업종 / 주소 / 선택 버튼 / 데모 데이터 표기
- 상태 메시지: 초기 안내, 검색 중, 결과 없음, 오류
- 결과 없음일 때: 주소 직접 입력 필드 + "이 주소로 계속하기" 버튼

### `/store/[storeId]/confirm`
- 선택된 가게 정보(이름/업종/주소) + 데모 데이터 표기
- 대표 메뉴 입력 필드
- 자동 분류 결과 안내 문구(카테고리, 신뢰도, 매칭 키워드)
- 메뉴 카테고리 select box (10종, 사용자가 수정 가능)
- "이번 주 분석하기" 버튼 (진행 중 상태 표시)

### `/report/[reportId]`
- 가게 정보 / 대표 메뉴 / 메뉴 카테고리 / 분석 기간 / 요약
- 이번 주 핵심 행동 Top 3 (각 항목: 제목, 본문, 근거, 우선순위, 신뢰도, 출처 ID)
- 7일 날씨 표
- 주변 상권 요약
- 축제·행사 목록 (거리, 영향 구분)
- 요일별 운영 가이드
- 근거 출처 목록 (제목, 기관, 연도, 설명, 해석 시 유의, URL)
- "다른 가게 분석" 버튼 / "분석 결과 공유" 버튼(UI만, 비활성)
- 면책 문구

## 3. 컴포넌트 목록과 역할

| 컴포넌트 | 경로 | 역할 |
|---|---|---|
| `Header` | `components/layout/Header.tsx` | 서비스명, 검색 링크 |
| `Footer` | `components/layout/Footer.tsx` | 최소 안내 문구 |
| `StoreSearchForm` | `components/search/StoreSearchForm.tsx` | 검색 입력 + 유효성 검사(React Hook Form + Zod) |
| `StoreResultList` | `components/search/StoreResultList.tsx` | 검색 결과 목록과 선택 이동 |
| `MenuCategorySelector` | `components/store/MenuCategorySelector.tsx` | 메뉴 카테고리 select box |
| `ReportSummary` | `components/report/ReportSummary.tsx` | 가게·메뉴·기간·요약·Top 3 |
| `RecommendationList` | `components/report/RecommendationList.tsx` | 권고 카드 목록(근거·출처 ID 포함) |
| `WeatherList` | `components/report/WeatherList.tsx` | 7일 날씨 표 |
| `CommercialAreaSummary` | `components/report/CommercialAreaSummary.tsx` | 상권 요약 |
| `FestivalList` | `components/report/FestivalList.tsx` | 행사 목록(거리·영향 구분) |
| `DailyGuideList` | `components/report/DailyGuideList.tsx` | 요일별 가이드 |
| `SourceList` | `components/report/SourceList.tsx` | 출처 목록 |
| `Disclaimer` | `components/common/Disclaimer.tsx` | 면책 문구 |
| `DemoBadge` | `components/common/DemoBadge.tsx` | 데모 데이터 표기 |
| `StateMessage` | `components/common/StateMessage.tsx` | 상태별 메시지 |

## 4. 데이터 타입

`frontend/types/index.ts` 에 정의되어 있습니다. **이름과 필드를 변경하지 마세요.**

- `Store` — 가게 (id, name, category, address, roadAddress, latitude, longitude, city, district, isDemoData)
- `MenuClassification` — 메뉴 자동 분류 (menuCategory, confidence, matchedKeywords, isDemoData)
- `AnalysisReport` — 리포트 전체
- `Recommendation` — 권고 1건 (title, text, type, priority, confidence, conditionType, basis, date, sourceIds)
- `Source` — 출처 (id, sourceType, title, organization, publicationYear, url, description, reliabilityNote)
- `FestivalEvent` — 행사 (distanceMeters, impactLevel, impactNote 포함)
- `WeatherDay` — 하루치 날씨 (condition, tempMax, tempMin, precipitationProbability, precipitationMm, pm10Grade)
- `DailyGuide` — 요일별 가이드 (date, dayOfWeek, weatherSummary, guides)

### 표시 값 매핑 참고
- `priority`: HIGH / MEDIUM / LOW
- `confidence`: HIGH / MEDIUM / LOW
- `type`: INVENTORY(재료·재고) / STAFFING(인력) / MENU(메뉴) / DELIVERY(포장·배달) / MARKETING(노출) / NOTICE(안내)
- `conditionType`: RAIN / HOT / COLD / DUST / WEEKEND / FESTIVAL / COMPETITION
- `impactLevel`: 직접 영향 가능 / 간접 영향 가능 / 영향 제한적 / 거리 정보 없음

## 5. 상태 정의

`ViewState = "initial" | "loading" | "success" | "empty" | "error"` 와 `isDemoData` 플래그를 사용합니다.

| 상태 | 화면 처리 |
|---|---|
| initial | 안내 문구 ("상호명 또는 주소를 입력해 검색해주세요.") |
| loading | "불러오는 중입니다..." (`role="status"`) |
| success | 실제 데이터 렌더링 |
| empty | "검색 결과가 없습니다." + 주소 직접 입력 |
| error | 오류 메시지 (`role="alert"`) |
| demoData | `DemoBadge` 표기 + 리포트 상단 `demoNotice` 문구 |

## 6. 디자인 적용 시 주의사항

1. **API 인터페이스와 타입 이름을 바꾸지 않습니다.** (`frontend/types/index.ts`, `frontend/lib/api.ts`)
2. **추천의 `sourceIds` 연결을 제거하지 않습니다.** 출처 추적이 이 서비스의 신뢰 근거입니다.
3. **면책 문구를 제거하지 않습니다.** (`Disclaimer` 컴포넌트)
4. **`isDemoData`가 true면 "데모 데이터" 표기를 유지합니다.**
5. **실제 매출 예측처럼 보이는 표현을 쓰지 않습니다.** ("매출 30% 상승", "매출 보장" 등 금지. "준비 권장", "점검 권장", "수요 변화 가능성" 사용)
6. **모바일 터치 타겟은 최소 44px 이상**을 유지합니다. (`globals.css`의 `input/select/button { min-height: 44px }`)
7. 로직은 페이지 컴포넌트(`app/**/page.tsx`)에, 표시는 `components/**`에 있습니다. 스타일 교체는 `components/**`와 CSS만 만지면 됩니다.
