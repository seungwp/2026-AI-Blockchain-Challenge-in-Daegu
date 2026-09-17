# 개발 규칙 (Codex · Claude 공통)

> `AGENTS.md`와 `CLAUDE.md`는 **같은 내용**입니다. 한쪽을 고치면 다른 쪽도 같이 고칩니다.

## 프로젝트
대구 음식점 사장님에게 날씨·축제·식자재 가격·경쟁점 공공데이터로 **이번 주 조언**을 주는 웹서비스.
2026 AI Blockchain Challenge in Daegu 출품작. 서류 마감 2026-09-20, 본선 2026-10-22. **블록체인 요소는 넣지 않음(AI만).**

제출용 본체는 **`golmok-one-week/` (골목 한 주)** 풀스택 MVP입니다. 상호명·주소 + 대표 메뉴를 입력하면
상권·이번 주 날씨·대구 축제·메뉴 특성을 결합해 **이번 주 운영 가이드**를 돌려줍니다.
디자인팀에 넘길 **개발용 MVP**이므로 디자인·브랜딩은 넣지 않습니다. (디자인팀 단계부터의 규칙은 golmok-one-week/docs/handoff-to-design.md 행동강령 참고)

## 역할 분담 (폴더 단위, 서로의 폴더는 수정 금지)
| 담당 | 노트북 | 폴더 | 할 일 |
|---|---|---|---|
| **Claude** | 내 노트북 | `golmok-one-week/` | 풀스택 MVP (Next.js 프론트 + Spring Boot 백엔드) |
| **Codex** | 팀원 노트북 | `web/` | (이전 단계) 휴대폰 기준 반응형 화면 프로토타입 |
| **Claude** | 내 노트북 | `pipeline/`, `data/`, `contracts/`, `docs/figures/` | 데이터 수집·모델·LLM 조언 → 화면용 JSON 내보내기 |
| 공통(읽기) | | `docs/design/`, `contracts/README.md`, `README.md` | 변경은 PR로 합의 |

- 두 영역은 **`contracts/` JSON 형식으로만 연결**됩니다. 형식을 바꿔야 하면 코드부터 고치지 말고 PR로 `contracts/README.md` 변경을 먼저 제안합니다.
- web은 `contracts/sample/`을 읽기만 하고 수정하지 않습니다. 데이터가 틀리면 이슈로 알립니다.

## 기술 스택

### golmok-one-week (제출용 본체)

**Frontend — `golmok-one-week/frontend/`**
- Next.js 최신 안정 버전(현재 16.3.5) + **App Router** + TypeScript
- HTTP는 **Axios** (`lib/api.ts` 한 곳에서만 호출, 실패 시 `lib/mock.ts` 데모 데이터로 폴백)
- 폼은 **React Hook Form + Zod** (`zodResolver`). 검색 폼·대표 메뉴 폼 모두 적용
- CSS는 `app/globals.css` 또는 CSS Module 위주. **Tailwind는 선택사항이고 현재 미사용**, 쓰더라도 최소 레이아웃 클래스만
- **UI 컴포넌트 라이브러리·애니메이션 라이브러리 사용 금지** (MUI·shadcn·Framer Motion·GSAP 등). 라이브러리 없는 순수 CSS 스타일·애니메이션(transition, @keyframes)은 디자인팀 단계부터 허용
- 시맨틱 HTML + 컴포넌트 책임 분리. 디자인 교체가 비즈니스 로직 변경을 요구하면 안 됨

**Backend — `golmok-one-week/backend/`**
- Java 21 · Spring Boot 3.x(현재 3.5.5) · Gradle
- Spring Web, Spring Data JPA, Spring Validation, Lombok, Springdoc OpenAPI(Swagger)
- 개발 DB는 **H2**(`jdbc:h2:mem:golmok`, MODE=MySQL). **MySQL은 profile 구조만 준비**
  (`application-mysql.yml`, `--spring.profiles.active=mysql`, 접속 정보는 `MYSQL_URL/USER/PASSWORD` 환경변수)
- 외부 데이터는 Provider 인터페이스 + Mock 구현. 실제 API는 `golmok.providers.*` 값으로 Adapter 교체
- 매출액·매출 증감률을 예측하지 않음. "운영 참고용 / 준비 권장 / 점검 권장" 표현만 사용하고 모든 권고에 `sourceIds` 부착

### 이전 단계 (유지)
- **web**: React + Vite + TypeScript + Tailwind CSS(레이아웃 유틸리티만), Node 24 (`.nvmrc`)
  - `vite.config.ts`: `base: './'` (dist 더블클릭 실행), 저장소 루트의 `contracts/`를 import하므로 `server.fs.allow`에 상위 폴더 허용
  - 라우팅 **HashRouter**, 데이터는 `contracts/sample/**/*.json`을 **import로 빌드에 포함** (fetch 금지: file:// 실행 시 막힘)
  - 데이터 접근은 `web/src/data.ts` 한 파일에서만 (나중에 API로 바꿀 때 이 파일만 수정)
  - 그래프 Recharts, 지도 Leaflet + OpenStreetMap(키 없음). 새 라이브러리는 꼭 필요할 때만
  - 색·글꼴·아이콘·그림자 등 시각 디자인 금지. 기본 요소와 레이아웃만
- **pipeline**: Python 3.13, `pipeline/requirements.txt`. 스크립트는 **저장소 루트에서** `py -X utf8 pipeline/<script>.py` 로 실행
- 배포: 정적 빌드(`web/dist`)를 NCP Object Storage에 업로드 (운영용/확인용 분리, 추후)

## 반드시 지킬 것
1. **API 키는 코드·커밋에 넣지 않음.** 루트 `.env`에서만 읽음 (`.env.example` 참고). web은 키가 전혀 필요 없음
2. **실존 가게 표시 범위**
   - `golmok-one-week/` (골목 한 주): 사장님이 **자기 가게를 상호명으로 검색**하는 서비스이므로 공개 데이터(식품 인허가)에 있는 **상호명·업태·주소·좌표를 그대로 노출**합니다. 관리번호는 노출하지 않습니다.
   - `web/` (가상 매출 데모): 종전대로 **익명화**합니다. 화면·JSON에 상호·관리번호·정확 좌표 금지, 가게는 `S1 치킨집 · 동네`로만, 지도는 동네 중심점만.
   - 이유: 골목 한 주는 실제 매출을 다루지 않고 공개된 영업 정보만 쓰지만, web은 **가상 매출**을 실존 가게에 붙이므로 특정되면 안 됩니다.
3. **가상 매출 표시**: 매출이 보이는 화면에 "가상 매출 데모" 문구
4. **검증 실패 조언 숨김**: `advice` 파일의 `validation` 마지막 `passed=false`면 조언을 표시하지 않음
5. **모델·예측 작업은 검증 단계와 그래프를 함께 만듦** (학습/검증 기간 분리, 기준선 비교, `docs/figures/`에 PNG)
6. LLM은 숫자를 계산하지 않음. 조언 속 숫자·날짜는 signals에 있는 값만 (검증 코드가 확인)

## Git
- 브랜치: Codex `codex/<작업>` (예 `codex/web-scaffold`), Claude `claude/<작업>` → GitHub PR → `main`
- `main`에 직접 푸시하지 않음. PR은 상대 에이전트(또는 사람)가 리뷰
- 작업 시작 전 `git pull origin main`, 담당 폴더 밖 변경이 diff에 있으면 커밋하지 말고 확인
- 커밋 메시지: `[web] 조언 상세 화면 추가`, `[pipeline] 조언 재생성`, `[contracts] prices.json 필드 추가` 처럼 영역 태그 + 한국어 요약
- 대용량 원본(`data/raw/licenses/*.xlsx`, `식품_*.csv`, 전국 경계)과 `.env`, `web/node_modules`, `web/dist`는 커밋 금지

## 완료 기준
- golmok-one-week: `backend/gradlew bootRun` 기동 + Swagger(`/swagger-ui.html`) 노출, `frontend/npm run build` 성공,
  390px 폭 가로 스크롤 없음, **백엔드를 꺼도 프론트가 데모 데이터로 전체 흐름 동작**
- web: `npm run build` 성공 + `web/dist/index.html` 더블클릭으로 모든 화면 동작 + 360px 폭 가로 스크롤 없음 (`docs/design/screens.md`)
- pipeline: 스크립트 실행 성공 + 검증 assert 통과 + `py -X utf8 pipeline/export_web_data.py`로 `contracts/sample/` 갱신

## 참고 문서
- **골목 한 주 인수인계**: `golmok-one-week/docs/handoff-to-design.md` (디자인팀), `golmok-one-week/docs/api-spec.md`, `golmok-one-week/docs/architecture.md`
- **골목 한 주 코드 지도 (코드 수정 전 먼저 확인)**: `golmok-one-week/docs/code-map.md` — 기능별 핵심 파일·API·의존관계. 그래프는 `golmok-one-week/graphify-out/`
- 화면 명세: `docs/design/screens.md`
- 데이터 형식: `contracts/README.md`
- 데이터 출처·실행 방법: `README.md`
