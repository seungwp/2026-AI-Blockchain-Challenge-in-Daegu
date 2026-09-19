# 디자인팀 인수인계 문서

> 2026-09-19: Figma Onboarding1~4 구현 완료. 아래의 기존 MVP 화면 설명 중
> 시작·검색·메뉴 화면은 [온보딩 구현 기록](onboarding-implementation.md)을 우선한다.
> 대표 메뉴는 단일 선택이며 데이터 연결 화면에는 하단 다음 버튼이 없다.

*최종 갱신: 2026-09-17*

현재 코드는 **기능 검증용 MVP**입니다. 디자인 요소(색상 테마, 아이콘, 카드 스타일)는 의도적으로 넣지 않아서, 이제부터 디자인팀이 입힐 차례입니다.
UI/UX를 전면 교체해도 비즈니스 로직이 깨지지 않도록 API 타입과 컴포넌트 책임을 분리해 두었습니다.

## 디자인 단계 행동강령 — 헷갈리면 여기부터

### 1. 지금 보이는 데이터는 데모여도, 걱정하지 않아도 됩니다

화면에 "데모 데이터" 배지가 뜨는 항목이 있습니다. **이건 데이터가 가짜라서가 아니라, 그 항목이 지금 이 순간 실API 대신 대체값을 쓰고 있다는 표시일 뿐입니다.**

- 가게 24,079곳·축제 17건·날씨·명절·식자재 가격은 **이미 실 API·실데이터로 연동이 끝나 있습니다.**
- 배지가 뜨는 경우는 딱 두 가지뿐입니다: ① 지금 이 컴퓨터에서 백엔드를 안 띄웠거나(그럼 프론트가 자동으로 예시 데이터로 대체), ② 사용자가 검색이 안 돼서 주소를 직접 입력한 가게인 경우.
- **배포 시점엔 서버에 API 키만 넣으면 지금 디자인한 화면 그대로 실데이터가 채워집니다.** 데이터 구조(타입)는 데모든 실데이터든 완전히 동일해서, 디자인은 배지 유무와 상관없이 지금 보이는 화면 그대로 진행하면 됩니다.
- 다만 **빈 값(`null`)·빈 배열은 실제로도 일어나는 정상 상황**입니다(예: 식자재 가격이 없는 메뉴 카테고리, 인근에 축제가 없는 주). "데이터가 이상해서 비어 보이나?" 의심하지 말고, 그 상태도 디자인해주세요. 각 컴포넌트에 이미 안내 문구가 있습니다("이 메뉴 카테고리에 연결된 식자재 품목이 없습니다" 등).

### 2. 애니메이션·스타일링

- **순수 CSS는 자유롭게 사용 가능**: `transition`, `@keyframes`, `transform`, hover 효과 등 전부 OK
- **외부 라이브러리는 설치하지 않습니다**: Framer Motion·GSAP·AOS 같은 애니메이션 라이브러리, MUI·shadcn·Ant Design 같은 UI 컴포넌트 라이브러리 금지
- 이유는 라이선스나 취향 문제가 아니라 **번들 크기·의존성 관리**입니다. 순수 CSS로 충분히 화려하게 만들 수 있습니다.

### 3. AI 요약(`aiSummary`)은 있어도 없어도 정상입니다

리포트 화면에 `.ai-summary` 클래스로 문장 하나가 더 뜨는 경우가 있습니다. Groq LLM이 만든 자연스러운 요약인데,
**항상 null일 수 있습니다**(API 실패·타임아웃 시). null이면 그냥 그 줄이 안 뜨고 기존 `summary`만 보입니다.
**이 필드가 있다는 가정으로 레이아웃을 짜지 마세요** — 없는 상태가 흔한 정상 상태입니다.

### 4. 챗봇(`components/report/ChatWidget.tsx`)은 위치만 고정, 스타일은 자유

리포트 화면 우측 하단에 떠 있는 플로팅 버튼입니다. `position: fixed; right; bottom`만 고정해뒀고 그 외 색상·크기·말풍선 모양은 전부 디자인팀 재량입니다.
사장님의 질문에 **지금 보고 있는 리포트 데이터만 근거로** 답합니다(일반 지식 챗봇 아님). 매출을 퍼센트로 예측하는 답은 절대 나오지 않도록 서버에서 막고 있으니, 그런 답이 실제로 나온다면 버그이므로 알려주세요.

### 5. 건드려도 되는 곳 / 건드리면 안 되는 곳

| 구분 | 경로 | 규칙 |
|---|---|---|
| ✅ 자유 | `components/**/*.tsx`의 스타일·className·레이아웃 | 완전히 자유. 구조(어떤 props를 받는지)는 유지 |
| ✅ 자유 | `app/globals.css`, 새로 만드는 CSS 파일 | 자유 |
| ⚠️ 주의 | `app/**/page.tsx` | JSX 마크업 구조는 조정 가능하나, API 호출·상태 관리 로직(`useEffect`, `useState`, `useForm`)은 건드리지 않기 |
| ❌ 금지 | `types/index.ts`, `lib/api.ts` | 필드명·함수 시그니처를 바꾸면 백엔드 연동이 끊깁니다 |

### 6. 그래도 절대 지켜야 하는 것 (아래 "6. 디자인 적용 시 주의사항"과 동일, 요약)

매출 예측 표현 금지 · 면책 문구 유지 · `sourceIds`/출처 표시 유지 · `isDemoData` 배지 유지 · 터치 타겟 44px 이상

---

## 0. 실행 방법

```
cd golmok-one-week/backend && ./gradlew bootRun     # 8080, Swagger: /swagger-ui.html
cd golmok-one-week/frontend && npm run dev          # 3000
```

**백엔드 없이도 동작합니다.** `frontend/lib/mock.ts`가 폴백 데이터를 제공하므로 `npm run dev`만으로 전체 화면 흐름을 확인할 수 있습니다.

## 1. 페이지별 목적 — 6단계 흐름

```
/  →  /search  →  /store/[storeId]/confirm  →  /report/[reportId]/area  →  /report/[reportId]
가게명·주소 입력 → 검색 결과 → 대표 메뉴·영업 형태 확인 → 주변 생활권 분석 → 이번 주 운영 컨설팅
```

| 경로 | 목적 |
|---|---|
| `/` | 서비스 소개 한 줄 + 검색 시작 진입점 |
| `/search` | 상호명·주소로 가게를 찾고 선택. 결과가 없으면 주소 직접 입력 |
| `/store/[storeId]/confirm` | 선택한 가게 확인, 대표 메뉴 입력, 메뉴 카테고리 자동 분류 결과 확인·수정, 분석 실행 |
| **`/report/[reportId]/area`** | **(신규)** 리포트 생성 직후 먼저 보여주는 생활권(경쟁 현황) 화면. 리포트 응답의 `commercialArea`만 뽑아 렌더 |
| `/report/[reportId]` | 이번 주 운영 가이드 결과. 새로고침해도 `reportId` 기준으로 동일한 결과 |

⚠️ **가게 상호명을 그대로 노출합니다.** 익명화(`S1 치킨집` 식)는 `web/`(가상 매출 데모)에만 적용되고, 골목 한 주는 공공 인허가 데이터(상호·업태·주소·좌표)를 그대로 씁니다. 관리번호만 노출하지 않습니다.

## 2. 페이지별 필수 UI 요소

### `/`
- 서비스명(골목 한 주), 한 줄 설명
- "가게 검색 시작" 버튼
- 매출 예측이 아니라는 안내 문구

### `/search`
- 상호명/주소 입력 필드 + 검색 버튼
- 검색 결과 목록: 가게명 / 업종 / 주소 / 선택 버튼 / 데모 데이터 표기(실가게는 대부분 표기 없음)
- 상태 메시지: 초기 안내, 검색 중, 결과 없음, 오류
- 결과 없음일 때: 주소 직접 입력 필드 + "이 주소로 계속하기" 버튼
- 검색 결과가 **최대 20건**으로 제한됩니다(대구 전역 음식점이 2만 건대라 서버에서 상한을 둠)

### `/store/[storeId]/confirm`
- 선택된 가게 정보(이름/업태/주소) + 데모 데이터 표기
- 대표 메뉴 입력 필드
- 자동 분류 결과 안내 문구(카테고리, 신뢰도, 매칭 키워드)
- 메뉴 카테고리 select box (10종, 사용자가 수정 가능)
- "이번 주 분석하기" 버튼 (진행 중 상태 표시) → 성공 시 `/report/[reportId]/area` 로 이동

### `/report/[reportId]/area` (신규)
- 가게명 + 주소
- 생활권 요약: 지역(구·동), 반경 500m 음식점 수, 유사 업종 수, 경쟁 강도
- 집적효과 근거 문구(음식점이 많을수록 매출도 함께 증가한다는 연구 인용) — **"경쟁점이 많아 불리하다"고 단정하지 않음**
- "이번 주 운영 컨설팅 보기" 버튼 → `/report/[reportId]`
- "다른 가게 분석" 버튼

### `/report/[reportId]`
- 가게 정보 / 대표 메뉴 / 메뉴 카테고리 / 분석 기간 / 요약
- 이번 주 핵심 행동 Top 3 (**최대** 3개, 이번 주 변수가 적으면 1~2개일 수 있음. 각 항목: 제목, 본문, 근거, 우선순위, 신뢰도, 출처 ID)
- 7일 날씨 표 (기온·강수·**습도**. 미세먼지 열은 없음 — 아래 3절 참고)
- 주변 생활권 요약 (area 페이지와 같은 컴포넌트 재사용)
- 축제·행사 목록 (거리, 영향 구분)
- **식자재 참고 가격** (신규 섹션 — 메뉴 카테고리에 대응하는 품목이 있을 때만 표시)
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
| `CommercialAreaSummary` | `components/report/CommercialAreaSummary.tsx` | 생활권 요약 — `/area` 페이지와 리포트 페이지 양쪽에서 재사용 |
| `FestivalList` | `components/report/FestivalList.tsx` | 행사 목록(거리·영향 구분) |
| **`IngredientPriceList`** | `components/report/IngredientPriceList.tsx` | **(신규)** 식자재 참고 가격 목록 |
| `DailyGuideList` | `components/report/DailyGuideList.tsx` | 요일별 가이드 |
| `SourceList` | `components/report/SourceList.tsx` | 출처 목록 |
| `Disclaimer` | `components/common/Disclaimer.tsx` | 면책 문구 |
| `DemoBadge` | `components/common/DemoBadge.tsx` | 데모 데이터 표기 |
| `StateMessage` | `components/common/StateMessage.tsx` | 상태별 메시지 |

## 4. 데이터 타입

`frontend/types/index.ts` 에 정의되어 있습니다. **이름과 필드를 변경하지 마세요.**

- `Store` — 가게 (id, name, category, address, roadAddress, latitude, longitude, city, district, isDemoData)
- `MenuClassification` — 메뉴 자동 분류 (menuCategory, confidence, matchedKeywords, isDemoData)
- `AnalysisReport` — 리포트 전체. `summary`(항상 있음)와 `aiSummary`(항상 null일 수 있음, 위 3절 참고) 둘 다 있음
- `Recommendation` — 권고 1건 (title, text, type, priority, confidence, conditionType, basis, date, sourceIds)
- `Source` — 출처 (id, sourceType, title, organization, publicationYear, url, description, reliabilityNote)
- `FestivalEvent` — 행사 (distanceMeters, impactLevel, impactNote 포함)
- `WeatherDay` — 하루치 날씨 (condition, tempMax, tempMin, precipitationProbability, precipitationMm, **humidity**, isDemoData, sourceId)
  - ⚠️ **`pm10Grade` 필드는 삭제됐습니다.** 미세먼지는 매출과 유의한 관계가 확인되지 않아(근거: `docs/coefficients.md`) 서비스에서 전면 제거했습니다. 디자인 시안에 미세먼지 UI가 남아 있다면 빼주세요.
  - `humidity`는 **4~6일차(중기예보 구간)엔 `null`**입니다. 대구 전역 기준 예보라 동네 단위 습도가 없기 때문입니다. `null`이면 "-"로 표시하세요.
- `DailyGuide` — 요일별 가이드 (date, dayOfWeek, weatherSummary, guides)
- **`IngredientPrice`** — **(신규)** 식자재 참고 가격 (item, unit, price, priceDate, probSpike, alert, vsNormalRatio, isDemoData, sourceId)
  - `unit`: "1kg", "1포기", "100g", "30구(1판)" 등 — **반드시 가격과 함께 표시**. 단위 없이 "배추 5,363원"만 보여주면 무엇 기준인지 알 수 없습니다.
  - `alert`가 `true`면 "향후 7일 내 급등 가능성" 신호지만 모델 정밀도가 약 20%로 낮습니다. 강한 경고 UI(빨간 배너 등)로 과장하지 말고 "확인 필요" 정도의 약한 표시를 권장합니다.
  - 메뉴 카테고리에 대응 품목이 없으면(냉면류·일식·양식·기타·공통) 이 배열은 **빈 배열**입니다. 섹션 자체를 숨기거나 "연결된 식자재 없음" 안내로 처리하세요.

### 표시 값 매핑 참고
- `priority`: HIGH / MEDIUM / LOW
- `confidence`: HIGH / MEDIUM / LOW
- `type`: INVENTORY(재료·재고) / STAFFING(인력) / MENU(메뉴) / DELIVERY(포장·배달) / MARKETING(노출) / NOTICE(안내)
- `conditionType`: RAIN / HOT / COLD / WEEKEND / **HOLIDAY**(신규, 추석 전날·당일·연휴기간·마지막날) / FESTIVAL / COMPETITION / **PRICE_SPIKE**(신규)
  - ~~DUST~~ 는 삭제됐습니다. 코드에 이 값이 나올 일이 없습니다.
- `impactLevel`: 직접 영향 가능(1km 이내) / 간접 영향 가능(3km 이내). **3km보다 먼 행사는 목록에 아예 포함되지 않아** 주변 행사가 빈 배열인 주가 흔합니다.

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

`isDemoData`는 이제 **가게·날씨·축제·상권·식자재 가격 중 하나라도 예시 값이면** true입니다. 현재는 미세먼지가 없어졌고 날씨·가게·축제·상권·식자재가 대부분 실데이터라, **정상적으로는 `isDemoData: false`가 뜨는 경우가 많습니다.** false일 때 "데모 데이터" 배지를 억지로 보여주지 마세요.

## 6. 디자인 적용 시 주의사항

1. **API 인터페이스와 타입 이름을 바꾸지 않습니다.** (`frontend/types/index.ts`, `frontend/lib/api.ts`)
2. **추천의 `sourceIds` 연결을 제거하지 않습니다.** 출처 추적이 이 서비스의 신뢰 근거입니다.
3. **면책 문구를 제거하지 않습니다.** (`Disclaimer` 컴포넌트)
4. **`isDemoData`가 true면 "데모 데이터" 표기를 유지합니다.**
5. **실제 매출 예측처럼 보이는 표현을 쓰지 않습니다.** ("매출 30% 상승", "매출 보장" 등 금지. "준비 권장", "점검 권장", "수요 변화 가능성" 사용)
6. **식자재 가격에는 반드시 단위를 함께 표시합니다.** 단위 없는 가격 숫자는 오해를 부릅니다.
7. **급등확률(`probSpike`)을 확정 예측처럼 강조하지 않습니다.** 모델 정밀도가 낮다는 사실이 출처(`sourceId: 14`)의 `reliabilityNote`에 이미 담겨 있으니, 화면에서도 "확인 참고용" 수준으로 유지하세요.
8. **모바일 터치 타겟은 최소 44px 이상**을 유지합니다. (`globals.css`의 `input/select/button { min-height: 44px }`)
9. 로직은 페이지 컴포넌트(`app/**/page.tsx`)에, 표시는 `components/**`에 있습니다. 스타일 교체는 `components/**`와 CSS만 만지면 됩니다.

## 7. 참고 문서

- `docs/api-spec.md` — 엔드포인트별 요청·응답 예시 (이 문서와 같이 갱신됨)
- `docs/architecture.md` — 백엔드 구조
- `docs/coefficients.md` — 모든 권고 문구가 근거로 삼는 연구·공공데이터 원문 인용. 디자인이 문구를 다듬을 때 "왜 이렇게 표현했는지" 확인용
