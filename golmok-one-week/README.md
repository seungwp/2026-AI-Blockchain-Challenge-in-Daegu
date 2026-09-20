# 장사메이트

대구 음식점 사장님이 가게와 대표 메뉴를 입력하면, 날씨·공휴일·지역 행사·식자재 가격·주변 음식점 정보를 결합해 **이번 주 운영 가이드**를 제공하는 웹서비스입니다.

> 운영 참고용 서비스입니다. 매출액·매출 증감률·이익·현금흐름을 예측하거나 보장하지 않습니다.

## 핵심 기능

- **가게 검색**: 대구 일반음식점 인허가 24,079곳에서 상호명·주소로 가게를 찾습니다.
- **메뉴 기반 분석**: 대표 메뉴를 입력하면 메뉴 카테고리를 분류하고, 업종에 맞는 식자재·운영 항목을 연결합니다.
- **이번 주 운영 가이드**: 날씨, 공휴일, 반경 3km 내 행사, 최근 식자재 가격 흐름, 반경 500m 내 음식점·유사업종 수를 바탕으로 최대 3개의 점검 항목을 제공합니다.
- **근거보기**: 각 가이드의 데이터 출처와 기준일을 확인할 수 있습니다.
- **식자재 가격**: 메뉴 카테고리에 맞는 품목의 최근 7일 가격 흐름과 급등 신호를 표시합니다.
- **리포트 기반 AI 챗봇**: 현재 리포트에 포함된 사실만 설명합니다. 서비스가 산출하지 않는 매출·이익 등 구체적 수치는 답하지 않습니다.
- **오늘 기준 재분석**: 최신 조회 가능한 데이터를 바탕으로 새 리포트를 생성합니다.

## 구성

```text
golmok-one-week/
├── frontend/  Next.js 16 · TypeScript · Axios · React Hook Form · Zod
├── backend/   Spring Boot 3.5 · Java 21 · JPA · H2
└── docs/      API 명세, 데이터 출처, 아키텍처, 화면·시스템 구조 자료
```

```text
사용자 → Next.js 웹 화면 → Spring Boot API → 운영 가이드 엔진
                                      ├→ H2 리포트·가게 데이터
                                      └→ 공공데이터·AI API
```

## 빠른 실행

### 요구 환경

- Java 21 이상
- Node.js 20 이상
- npm

### 1. 백엔드 실행

```powershell
cd golmok-one-week\backend
.\gradlew.bat bootRun
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 개발용 H2 콘솔: `http://localhost:8080/h2-console`

### 2. 프론트엔드 실행

새 터미널에서 실행합니다.

```powershell
cd golmok-one-week\frontend
Copy-Item .env.local.example .env.local
npm install
npm run dev
```

브라우저에서 `http://localhost:3000`을 엽니다.

### 3. 빌드 확인

```powershell
cd golmok-one-week\frontend
npm run build

cd ..\backend
.\gradlew.bat compileJava
```

## 환경변수

외부 API 키가 없어도 전체 화면 흐름은 동작합니다. 키가 없거나 외부 API 조회에 실패하면, 서비스는 저장된 공공데이터 또는 안내 가능한 범위의 대체 흐름을 사용합니다.

실시간 연동과 AI 챗봇을 사용하려면 저장소 루트의 `.env`에 아래 값을 설정합니다. `.env`와 `frontend/.env.local`은 Git에 포함하지 않습니다.

```properties
DATA_GO_KR_KEY=
KAMIS_CERT_KEY=
KAMIS_CERT_ID=
GROQ_API_KEY=
NAVER_MAP_CLIENT_ID=
NAVER_MAP_CLIENT_SECRET=
```

프론트엔드의 기본 API 주소는 `http://localhost:8080`이며, 필요하면 `frontend/.env.local`의 `NEXT_PUBLIC_API_BASE_URL`로 변경합니다.

## 데이터 출처와 적용 범위

| 데이터 | 활용 |
|---|---|
| 행정안전부 식품위생업소 인허가 | 가게 검색, 업태·주소·좌표, 반경 500m 음식점·유사업종 집계 |
| 기상청 단기·중기예보 | 이번 주 날씨 기반 운영 점검 |
| 한국천문연구원 특일 정보 | 공휴일·명절 일정 확인 |
| 한국관광공사 TourAPI | 반경 3km 내 예정 행사 확인 |
| KAMIS 농산물유통정보 | 식자재 가격·최근 가격 흐름 |

상세한 데이터 기준과 수집 상태는 [docs/data-sources.md](docs/data-sources.md)를 참고하세요.

## 화면 흐름

```text
가게 찾기 → 가게 확인 → 대표 메뉴 입력 → 분석 중 → 이번 주 처방
                                                ├→ 근거보기
                                                ├→ 생활권 현황
                                                ├→ 처방 이력
                                                ├→ AI 챗봇
                                                └→ 마이페이지
```

백엔드가 실행되지 않은 상태에서도 프론트는 `frontend/lib/mock.ts`의 대체 데이터로 검색부터 리포트 화면까지 확인할 수 있습니다. 실제 공공데이터 기반 결과를 확인하려면 백엔드를 함께 실행하세요.

## 주요 문서

| 문서 | 내용 |
|---|---|
| [docs/api-spec.md](docs/api-spec.md) | REST API 명세 |
| [docs/architecture.md](docs/architecture.md) | 시스템 구조와 리포트 생성 흐름 |
| [docs/data-sources.md](docs/data-sources.md) | 공공데이터 출처와 연동 상태 |
| [docs/coefficients.md](docs/coefficients.md) | 운영 가이드의 연구 근거 |
| [docs/figures/service-flow.png](docs/figures/service-flow.png) | 서비스 흐름도 |

## 제출용 압축 시 제외할 항목

아래 파일은 용량·보안상 제출 ZIP에서 제외합니다.

```text
frontend/node_modules/
frontend/.next/
backend/build/
backend/build-server/
backend/.gradle/
.env
frontend/.env.local
.idea/
.git/
```
