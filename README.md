# 장사메이트

**2026 AI Blockchain Challenge in Daegu** 출품작입니다.

장사메이트는 대구 음식점 사장님이 가게와 대표 메뉴를 입력하면, 날씨·공휴일·지역 행사·식자재 가격·주변 음식점 정보를 결합해 **이번 주 운영 가이드**를 제공하는 웹서비스입니다.

> 공공데이터를 활용한 운영 참고용 서비스입니다. 매출액·매출 증감률·이익·현금흐름을 예측하거나 보장하지 않습니다.

## 제출용 본체

제출 및 실행 대상은 [`golmok-one-week/`](golmok-one-week/README.md)입니다.

```text
golmok-one-week/
├── frontend/  Next.js 16 · TypeScript
├── backend/   Spring Boot 3.5 · Java 21 · H2
└── docs/      API, 데이터 출처, 아키텍처, 서비스 자료
```

세부 기능, 환경변수, 실행·빌드 방법은 [golmok-one-week README](golmok-one-week/README.md)를 참고하세요.

## 핵심 기능

- 대구 일반음식점 인허가 24,079곳 기반 가게 검색
- 대표 메뉴 기반 카테고리 분류와 메뉴별 식자재 연결
- 날씨·공휴일·행사·식자재 가격·주변 현황을 결합한 이번 주 운영 가이드
- 권고별 데이터 출처와 기준일을 확인하는 근거보기
- 최근 7일 식자재 가격 그래프와 급등 신호
- 현재 리포트 안의 사실만 설명하는 AI 챗봇
- 오늘 기준 재분석과 브라우저 기반 처방 이력

## 빠른 실행

```powershell
# 백엔드
cd golmok-one-week\backend
.\gradlew.bat bootRun

# 새 터미널에서 프론트엔드
cd golmok-one-week\frontend
Copy-Item .env.local.example .env.local
npm install
npm run dev
```

- 프론트엔드: `http://localhost:3000`
- 백엔드 API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

빌드 확인:

```powershell
cd golmok-one-week\frontend
npm run build

cd ..\backend
.\gradlew.bat compileJava
```

## 데이터 출처

| 데이터 | 활용 |
|---|---|
| 행정안전부 식품위생업소 인허가 | 가게 검색, 주변 음식점·유사업종 집계 |
| 기상청 단기·중기예보 | 날씨 기반 운영 점검 |
| 한국천문연구원 특일 정보 | 공휴일·명절 일정 |
| 한국관광공사 TourAPI | 반경 3km 내 예정 행사 |
| KAMIS 농산물유통정보 | 식자재 가격과 가격 흐름 |

자세한 기준과 연동 상태는 [데이터 출처 문서](golmok-one-week/docs/data-sources.md)를 참고하세요.

## 환경변수와 보안

외부 API 키는 저장소 루트 `.env`에만 설정하며 Git에 포함하지 않습니다.

```properties
DATA_GO_KR_KEY=
KAMIS_CERT_KEY=
KAMIS_CERT_ID=
GROQ_API_KEY=
NAVER_MAP_CLIENT_ID=
NAVER_MAP_CLIENT_SECRET=
```

API 키가 없더라도 기본 화면 흐름과 저장된 공공데이터 기반 기능은 확인할 수 있습니다. 실제 API 연동과 AI 챗봇에는 해당 키가 필요합니다.

## 기타 폴더

`pipeline/`, `contracts/`, `web/`, `data/`는 초기 데이터 수집·분석·프로토타입 작업 자료입니다. 제출용 웹서비스의 실행에는 `golmok-one-week/`을 사용합니다.

## 주요 문서

| 문서 | 내용 |
|---|---|
| [서비스 README](golmok-one-week/README.md) | 기능, 실행 방법, 제출 ZIP 구성 |
| [API 명세](golmok-one-week/docs/api-spec.md) | REST API 명세 |
| [아키텍처](golmok-one-week/docs/architecture.md) | 시스템과 리포트 생성 흐름 |
| [데이터 출처](golmok-one-week/docs/data-sources.md) | 공공데이터 연동 현황 |
