# 골목 한 주 코드 지도 (Claude Code용)

> 2026-09-18 추가: `scripts/collect_public_data.py` → `resources/data/*{enrichment,details,history}.json`
> → `service/PublicDataSnapshots.java` → DataSeeder(상가 업종·행사) / IngredientPriceService(가격 이력).
> 주소 직접 입력은 `provider/JusoAddressProvider.java`의 도로명주소 검색을 거친다.
> 좌표 미확인은 거리 분석 생략. API 추가 필드·갱신 명령은 [공공데이터 연결 현황](public-data-integration.md) 참고.

*생성: 2026-09-17 · 같은 날 리팩토링 반영(FestivalService·IngredientPriceService 분리, SourceCatalog→entity, Mock→Db 이름 변경) · Graphify 그래프(`golmok-one-week/graphify-out/`) + 실제 파일 확인 기반*

**사용법:** 기능을 고칠 때 이 문서의 "기능별 지도"에서 핵심 파일만 먼저 연다. 전체 탐색은 하지 않는다.
경로 약어: `B:` = `backend/src/main/java/com/golmok/oneweek/`, `R:` = `backend/src/main/resources/`, `T:` = `backend/src/test/java/com/golmok/oneweek/`, `F:` = `frontend/`

---

## 1. 전체 구조

```
golmok-one-week/
├── backend/   Spring Boot 3.5.5 · Java 21 · Gradle · H2(in-memory)   ← REST API, 규칙 엔진, 외부 API, LLM
│   ├── src/main/java/com/golmok/oneweek/
│   │   ├── controller/  ApiControllers.java (컨트롤러 5개가 한 파일에 static class로)
│   │   ├── service/     리포트·가게·상권·메뉴분류·LLM·시드
│   │   ├── rule/        WeeklyGuideRuleEngine.java (권고 생성)
│   │   ├── provider/    외부 데이터 연동(날씨·명절·축제·가게검색·식자재)
│   │   ├── entity/ repository/ dto/ exception/ config/
│   └── src/main/resources/  application*.yml, data/*.csv(시드 원본)
├── frontend/  Next.js 16.3.5 App Router · TypeScript · Axios · RHF+Zod   ← 화면
│   ├── app/         페이지(라우트)
│   ├── components/  common · layout · report · search · store
│   ├── lib/         api.ts(백엔드 호출 유일 창구) · mock.ts(백엔드 실패 시 폴백)
│   └── types/index.ts  API 응답 타입(백엔드 DTO와 1:1)
├── docs/      api-spec · architecture · coefficients(근거) · data-sources · handoff-to-design · figures/
└── graphify-out/  그래프 산출물 (graph.json, GRAPH_REPORT.md, graph.html)
```

프론트↔백엔드 연결은 **HTTP만**(`F:lib/api.ts` → `B:controller/ApiControllers.java`). 코드 공유 없음.

## 2. 진입점

| 구분 | 파일 | 역할 |
|---|---|---|
| 백엔드 기동 | `B:GolmokOneWeekApplication.java` | `main` |
| 백엔드 기동 직후 | `B:service/DataSeeder.java` | `ApplicationRunner`: `R:data/*.csv` 5개를 H2에 적재 |
| 백엔드 요청 | `B:controller/ApiControllers.java` | 모든 REST 엔드포인트 |
| 백엔드 설정 | `R:application.yml` | 키(`golmok.keys.*`), Provider 선택(`golmok.providers.*`), 루트 `.env` 자동 import |
| 프론트 레이아웃 | `F:app/layout.tsx` | Header/Footer |
| 프론트 첫 화면 | `F:app/page.tsx` | `/search`로 링크 |

## 3. API ↔ 프론트 호출 매핑 (확인됨)

| 엔드포인트 | 컨트롤러 → 서비스 | 프론트 함수 (`F:lib/api.ts`) | 호출하는 화면 | mock 폴백 (`F:lib/mock.ts`) |
|---|---|---|---|---|
| `GET /api/health` | HealthController | (없음) | – | – |
| `GET /api/stores/search?keyword&city` | StoreController → `StoreService.search` → `StoreSearchProvider` | `searchStores` | `F:app/search/page.tsx` | `mockStores` |
| `GET /api/stores/{storeId}` | StoreController → `StoreService.get` | `getStore` | `F:app/store/[storeId]/confirm/page.tsx` | `mockStore` |
| `POST /api/stores/manual` | StoreController → `StoreService.createFromAddress` | `createStoreFromAddress` | `F:app/search/page.tsx` | 인라인 객체 |
| `POST /api/menu/classify` | MenuController → `MenuClassificationService.classify` | `classifyMenu` | confirm 페이지 | `mockClassify` |
| `POST /api/reports` | ReportController → `ReportService.create` | `createReport` | confirm 페이지 → `/report/{id}/area`로 이동 | `mockReport` |
| `GET /api/reports/{reportId}` | ReportController → `ReportService.get` | `getReport` | `F:app/report/[reportId]/area/page.tsx`, `F:app/report/[reportId]/page.tsx` | `mockReportById` |
| `POST /api/reports/{reportId}/chat` | ReportController → `ReportService.chat` → `ReportChatService.ask` | `askReportQuestion` | `F:components/report/ChatWidget.tsx` | `mockChatAnswer` |
| `GET /api/sources`, `/api/sources/{id}` | SourceController → `SourceRepository` (서비스 없음) | (없음, 출처는 리포트 응답에 포함) | – | – |

화면 흐름: `/` → `/search` → `/store/[storeId]/confirm` → `/report/[reportId]/area` → `/report/[reportId]`

## 4. 리포트 생성 데이터 흐름 (`B:service/ReportService.java#create`)

```
CreateRequest(storeId, mainMenu, menuCategory?)
 ├─ StoreService.getEntity ─────────────── StoreRepository (H2 stores)
 ├─ MenuClassificationService.classify ── (menuCategory 없을 때)
 ├─ weather()  → WeatherProvider ───────── KmaWeatherProvider(기상청) | MockWeatherProvider
 ├─ FestivalService.nearby ────────────── FestivalProvider=DbFestivalProvider(H2 festival_events, 실데이터) → 거리·3km 필터
 ├─ CommercialAreaService.summarize ───── StoreRepository 경계상자 → 반경 500m 집계
 ├─ HolidayProvider.classify ───────────── KasiHolidayProvider(천문연) → ChuseokClassifier | MockHolidayProvider
 ├─ IngredientPriceService.forCategory ─── MenuIngredientMap → IngredientPriceRepository(스냅샷) + KamisPriceProvider(실시간)
 ├─ WeeklyGuideRuleEngine.evaluate ─────── MenuRuleRepository(H2 menu_rules) → topActions·dailyGuides·summary
 ├─ LlmAdviceService.summarize ─────────── GroqChatClient → LlmNumberGuard(숫자 검증) → aiSummary(null 가능)
 └─ AnalysisReportRepository.save ──────── 각 조각을 JSON 컬럼으로 저장 → toResponse(출처 모아 ReportResponse)
```
`ReportService.get`은 저장된 JSON을 다시 읽어 같은 응답을 만든다(외부 API 재호출 없음).

## 5. DB·데이터 (H2 in-memory, 기동마다 재생성)

| 엔티티 (`B:entity/`) | 저장소 (`B:repository/`) | 시드 원본 (`R:data/`) | 사용처 |
|---|---|---|---|
| `Store` | `StoreRepository` (검색·경계상자 쿼리) | `daegu_stores.csv` (24,079곳) | StoreService, DbStoreSearchProvider, CommercialAreaService |
| `FestivalEvent` | `FestivalEventRepository` (기간 겹침) | `daegu_festivals.csv` (17건) | DbFestivalProvider |
| `MenuRule` | `MenuRuleRepository` (`findByMenuCategoryIn`) | `menu_rules.csv` (37건) | WeeklyGuideRuleEngine |
| `IngredientPrice` | `IngredientPriceRepository` | `ingredient_prices.csv` (8품목) | IngredientPriceService |
| `Source` | `SourceRepository` | `sources.csv` (14건, ID 고정) | ReportService.toResponse, SourceController |
| `AnalysisReport` | `AnalysisReportRepository` | (생성 시 저장) | ReportService |

- enum 전부: `B:entity/Enums.java` (MenuCategory 한글 라벨 직렬화, ConditionType, RecommendationType, Priority, Confidence, SourceType)
- 코드에서 쓰는 고정 출처 ID: `B:entity/SourceCatalog.java`
- 스키마는 JPA `ddl-auto: create-drop` (마이그레이션 없음). MySQL은 `R:application-mysql.yml` 프로필 구조만.

## 6. 설정 키 → 사용 클래스 (확인됨)

| 키 | 클래스 |
|---|---|
| `golmok.keys.data-go-kr` | `KmaWeatherProvider`, `KasiHolidayProvider` |
| `golmok.keys.kamis-cert-key/id` | `KamisPriceProvider` |
| `golmok.keys.groq-api-key/model` | `GroqChatClient` (기본 `openai/gpt-oss-120b`) |
| `golmok.cors-allowed-origins` | `B:config/WebConfig.java` |
| `golmok.providers.weather` = kma\|mock | `KmaWeatherProvider` / `MockWeatherProvider` (`@ConditionalOnProperty`) |
| `golmok.providers.holiday` = kasi\|mock | `KasiHolidayProvider` / `MockHolidayProvider` |
| `golmok.providers.festival`, `store-search` = mock | `DbFestivalProvider`, `DbStoreSearchProvider` (H2 시드 실데이터, 대체 구현 없음. 설정값은 호환 위해 mock 유지) |
| `spring.config.import` | 루트 `../../.env`를 속성으로 읽음 (작업 폴더가 `backend` 기준) |

프론트: `NEXT_PUBLIC_API_BASE_URL` (`F:lib/api.ts`, 기본 `http://localhost:8080`, `F:.env.local`)

---

## 7. 기능별 지도

형식: **디렉터리 / 핵심 파일 / 진입점 / API / 의존관계**

### 7-1. 가게 검색 · 주소 직접 입력
- 디렉터리: `B:service`, `B:provider`, `F:app/search`, `F:components/search`
- 핵심 파일: `B:service/StoreService.java`, `B:provider/DbStoreSearchProvider.java`(최대 20건, 직접입력 좌표=대구시청 임시값), `B:repository/StoreRepository.java`, `F:app/search/page.tsx`, `F:components/search/StoreSearchForm.tsx`, `StoreResultList.tsx`
- 진입점: `StoreController.search` / `manual`
- API: `GET /api/stores/search`, `POST /api/stores/manual`, `GET /api/stores/{id}`
- 의존: StoreService → `Providers.StoreSearchProvider`, StoreRepository, `dto/StoreResponse`

### 7-2. 대표 메뉴 자동 분류
- 핵심 파일: `B:service/MenuClassificationService.java`(키워드 사전), `B:dto/MenuDtos.java`, `B:entity/Enums.java#MenuCategory`, `F:app/store/[storeId]/confirm/page.tsx`, `F:components/store/MenuCategorySelector.tsx`, `F:types/index.ts#MENU_CATEGORIES`
- 진입점: `MenuController.classify` (리포트 생성 시 카테고리 없으면 `ReportService.create`도 호출)
- API: `POST /api/menu/classify`
- 의존: 프론트 mock 키워드는 `F:lib/mock.ts#KEYWORDS`에 따로 있음 → 키워드 추가 시 **두 곳 모두** 수정

### 7-3. 리포트 생성·조회 (오케스트레이션)
- 핵심 파일: `B:service/ReportService.java`(157줄, 각 조각 서비스를 호출해 조합·저장·응답 변환), `B:dto/ReportDtos.java`(응답 레코드 전부), `B:entity/AnalysisReport.java`
- 진입점: `ReportController.create` / `get`
- API: `POST /api/reports`, `GET /api/reports/{id}`
- 의존: 4장 흐름 참고. 응답 필드 변경 시 `B:dto/ReportDtos.java` + `F:types/index.ts` + `F:lib/mock.ts` + `docs/api-spec.md` 동시 수정

### 7-4. 운영 권고 규칙 (조건 → 권고 문구·우선순위·Top3·요약 문장)
- 디렉터리: `B:rule`, `R:data`
- 핵심 파일: `B:rule/WeeklyGuideRuleEngine.java`(284줄), `R:data/menu_rules.csv`(문구·출처·신뢰도), `B:entity/MenuRule.java`(conditionValue 해석 주석)
- 진입점: `WeeklyGuideRuleEngine.evaluate` (ReportService에서 호출)
- API: 없음 (리포트 응답의 `topActions`, `dailyGuides`, `summary`)
- 의존: MenuRuleRepository, ReportDtos, Enums. 문구만 바꿀 땐 **CSV만** 수정. 근거는 `docs/coefficients.md`
- 테스트: `T:rule/RuleCleanupTest.java`, `HolidayMatchTest.java`, `FestivalDistanceBandTest.java`

### 7-5. 날씨 (기상청 단기·중기예보)
- 핵심 파일: `B:provider/KmaWeatherProvider.java`(308줄, 가장 큼), `B:provider/KmaGrid.java`(위경도→격자), `B:provider/MockWeatherProvider.java`(키 없음·실패 시 채움값), `B:provider/Providers.java#WeatherProvider`
- 진입점: `ReportService.weather()`
- 의존: KmaWeatherProvider → KmaGrid, MockWeatherProvider, SourceCatalog. 표시: `F:components/report/WeatherList.tsx`
- 테스트: `T:provider/KmaGridTest.java`

### 7-6. 명절 (추석)
- 핵심 파일: `B:provider/KasiHolidayProvider.java`, `B:provider/ChuseokClassifier.java`, `B:provider/MockHolidayProvider.java`
- 진입점: `ReportService.create` → `HolidayProvider.classify`
- 의존: 결과 코드(CHUSEOK_*)는 `menu_rules.csv`의 HOLIDAY 행과 `WeeklyGuideRuleEngine.holidayLabel`에서 사용
- 테스트: `T:provider/ChuseokClassifierTest.java`, `KasiHolidayProviderLiveTest.java`

### 7-7. 축제·행사
- 핵심 파일: `B:service/FestivalService.java`(거리·3km 필터·영향 문구), `B:provider/DbFestivalProvider.java`, `B:repository/FestivalEventRepository.java`, `R:data/daegu_festivals.csv`, `F:components/report/FestivalList.tsx`
- 진입점: `ReportService.create` → `FestivalService.nearby`
- 의존: 거리 계산은 `CommercialAreaService.distanceMeters`(static) 재사용

### 7-8. 주변 상권 (반경 500m 경쟁 현황)
- 핵심 파일: `B:service/CommercialAreaService.java`, `B:repository/StoreRepository.java#findByCityAndLatitudeBetweenAndLongitudeBetween`, `F:app/report/[reportId]/area/page.tsx`, `F:components/report/CommercialAreaSummary.tsx`
- 의존: StoreRepository, SourceCatalog. 테스트 `T:service/DongOfTest.java`

### 7-9. 식자재 가격·급등 신호
- 핵심 파일: `B:provider/KamisPriceProvider.java`(실시간 가격), `B:service/MenuIngredientMap.java`(카테고리→품목), `B:repository/IngredientPriceRepository.java` + `R:data/ingredient_prices.csv`(급등확률 스냅샷), `B:service/IngredientPriceService.java`(실시간 가격+스냅샷 병합, 진입점 `forCategory`), `F:components/report/IngredientPriceList.tsx`
- 의존: 급등 권고 규칙은 `menu_rules.csv` PRICE_SPIKE 행. 확률 모델 원본은 저장소 루트 `pipeline/price_spike_model.py`(이 폴더 밖)
- 테스트: `T:service/MenuIngredientMapTest.java`, `T:provider/KamisPriceProviderLiveTest.java`

### 7-10. AI 요약 (aiSummary)
- 핵심 파일: `B:service/LlmAdviceService.java`(프롬프트·600토큰), `B:service/GroqChatClient.java`(HTTP·reasoning_effort=low·잘린 응답 거부), `B:service/LlmNumberGuard.java`(입력에 없는 숫자 차단)
- 진입점: `ReportService.create` → `LlmAdviceService.summarize`
- 의존: GroqChatClient ← `golmok.keys.groq-*`. 표시: `F:components/report/ReportSummary.tsx`
- 테스트: `T:service/LlmNumberGuardTest.java`

### 7-11. 리포트 챗봇
- 핵심 파일: `B:service/ReportChatService.java`(시스템 프롬프트·매출 예측 거절·재시도·history role 강등), `B:dto/ChatDtos.java`, `F:components/report/ChatWidget.tsx`
- 진입점: `ReportController.chat` → `ReportService.chat` → `ReportChatService.ask`
- API: `POST /api/reports/{reportId}/chat`
- 의존: GroqChatClient, LlmNumberGuard, ReportDtos(리포트를 사실 JSON으로 변환)

### 7-12. 리포트 화면 표시
- 핵심 파일: `F:app/report/[reportId]/page.tsx`(섹션 조합), `F:components/report/*.tsx`(ReportSummary, RecommendationList, DailyGuideList, WeatherList, CommercialAreaSummary, FestivalList, IngredientPriceList, SourceList, ChatWidget), `F:components/common/*`(DemoBadge, Disclaimer, StateMessage)
- 의존: 전부 `F:types/index.ts`만 참조, 데이터 조회는 페이지의 `getReport` 한 번

### 7-13. 백엔드 없이 동작 (데모 폴백)
- 핵심 파일: `F:lib/api.ts#withFallback`, `F:lib/mock.ts`(DEMO_STORES·데모 날씨·키워드·데모 리포트)
- 규칙: API 함수 추가 시 mock 함수도 함께 추가

### 7-14. 출처(근거) 목록
- 핵심 파일: `R:data/sources.csv`, `B:entity/Source.java`, `B:dto/SourceResponse.java`, `B:entity/SourceCatalog.java`, `B:service/ReportService.java#toResponse`(리포트에 인용된 출처만 모음), `F:components/report/SourceList.tsx`
- API: `GET /api/sources`, `GET /api/sources/{id}` / 근거 설명 문서: `docs/coefficients.md`

### 7-15. 에러 응답 · CORS · Swagger
- 에러: `B:exception/GlobalExceptionHandler.java`, `NotFoundException.java`, `B:dto/ErrorResponse.java`
- CORS·OpenAPI 정보: `B:config/WebConfig.java` / Swagger 경로: `R:application.yml#springdoc`

---

## 8. 수정 영향이 큰 공유 파일 (먼저 확인)

| 파일 | 참조하는 쪽 |
|---|---|
| `F:types/index.ts` | 프론트 페이지 4개(search·confirm·area·report)·컴포넌트 대부분, api.ts, mock.ts |
| `B:dto/ReportDtos.java` | ReportService, WeeklyGuideRuleEngine, 모든 Provider, LlmAdviceService, ReportChatService, 테스트 |
| `B:entity/Enums.java` | DTO, 엔티티, 규칙 엔진, 메뉴 분류, DataSeeder, 저장소 |
| `B:provider/Providers.java` | Provider 인터페이스 4개 (구현체 전부, ReportService, StoreService, FestivalService) |
| `B:service/ReportService.java` | 백엔드 의존이 가장 많이 모이는 곳 (조각 서비스·Provider·저장소 12개 + ObjectMapper 주입) |
| `R:data/menu_rules.csv` | 규칙 문구·출처·신뢰도의 단일 원본 |

## 9. 그래프 사용 팁·한계

- 질문형 탐색: 저장소 루트가 아니라 `golmok-one-week`에서 `graphify query "질문"` (그래프: `golmok-one-week/graphify-out/graph.json`)
- 코드 변경 후 갱신: `/graphify golmok-one-week --update`
- **한계:** 같은 Java 패키지 안의 참조(import 없이 쓰는 `MenuIngredientMap`, `LlmNumberGuard`, `ChuseokClassifier`, `KmaGrid`)는 그래프 import 관계에서 빠질 수 있다 → 이 문서의 의존 항목을 우선한다.
- 커뮤니티 자동 분류는 결합도가 낮다(0.04~0.07인 큰 묶음이 있음). 기능 찾기는 7장을 기준으로 한다.
- `frontend/AGENTS.md`의 "This is NOT the Next.js you know" 블록은 `next dev`가 생성하는 문구다(`node_modules/next/dist/server/lib/generate-agent-files.js` 존재 확인).
