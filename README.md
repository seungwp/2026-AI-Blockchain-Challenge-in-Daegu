# 동네 주치의 — 2026 AI Blockchain Challenge in Daegu

대구 음식점 사장님에게 날씨·축제·식자재 가격·경쟁점 공공데이터로 **이번 주 조언**을 주는 휴대폰 웹서비스.
개발 규칙은 `AGENTS.md`(= `CLAUDE.md`), 화면 명세는 `docs/design/screens.md`, 데이터 형식은 `contracts/README.md`.

## 구조
```
pipeline/     데이터 수집·가상 매출·모델·LLM 조언·웹용 JSON 내보내기 (Python, 담당 Claude)
contracts/    pipeline → web 데이터 계약 (README + sample JSON)
web/          휴대폰 기준 반응형 웹 화면 (React+Vite+TS+Tailwind, 담당 Codex)
data/         원본(raw)·가공 결과(processed)
docs/         화면 명세(design), 모델 검증 그래프(figures), 공모전 문서
```

## 실행
```bash
# 웹 (API 키 불필요)
cd web && npm install && npm run dev      # 개발
npm run build                              # web/dist/index.html 더블클릭으로도 실행

# 파이프라인 (저장소 루트에서)
py -m pip install -r pipeline/requirements.txt
py -X utf8 pipeline/export_web_data.py     # 기존 결과 → contracts/sample (키 불필요)
py -X utf8 pipeline/make_advice.py 2026-06-29 2026-09-14   # 조언 재생성 (.env의 NVIDIA_API_KEY 필요)
```

## 데이터 출처

모든 데이터는 공공데이터이며, 이용조건은 각 출처 페이지를 따릅니다.

| 데이터 | 경로 | 출처 | 기간·범위 | git |
|---|---|---|---|---|
| 대구 과거 날씨 (ASOS 일자료) | `data/raw/weather/` | 기상청 기상자료개방포털 (https://data.kma.go.kr), 대구 관측소 143 | 2023-01-01 ~ 2026-09-13 | O |
| 대구 날씨 예보 (단기예보) | `data/raw/forecast/` | 공공데이터포털 기상청_단기예보 조회서비스 (https://www.data.go.kr), 격자 nx=89, ny=90 | 2026-09-14 05시 발표분 | O |
| 대구 문화축제 | `data/raw/festival/` | 공공데이터포털 대구광역시_문화축제 (https://www.data.go.kr/data/15130238/fileData.do), 공공누리 제4유형(출처표시·상업적 이용금지·변경금지) | 2024년 32건 | O |
| 대구 행사·축제 (좌표 포함) | `data/raw/festival/daegu_festival_tourapi.csv` | 공공데이터포털 한국관광공사_국문 관광정보 서비스_GW `searchFestival2` (https://www.data.go.kr/data/15101578/openapi.do), `pipeline/fetch_festival.py` | 2026년 17건 | O |
| 농축수산물 품목·등급 코드표 | `data/raw/kamis/` | KAMIS 농산물유통정보 Open-API (https://www.kamis.or.kr) | - | O |
| 대구 소매가격 (식자재 8품목) | `data/raw/kamis/daegu_retail_daily.csv` | KAMIS Open-API `periodRetailProductList`, 지역코드 2200(대구), `pipeline/fetch_kamis.py` | 2023-01-01 ~ 2026-09-13 | O |
| 일반음식점 인허가 | `data/raw/licenses/daegu_restaurants.csv` | 공공데이터포털 행정안전부_식품_일반음식점 (https://www.data.go.kr/data/15045016/fileData.do), 대구 파일에서 분석용 8개 컬럼만 추출 | 2026-09-14 갱신분, 97,193곳 | O |
| 대구 행정동 경계 | `data/raw/boundary/daegu_dong_ver20250401.geojson` | vuski/admdongkor `ver20250401` (https://github.com/vuski/admdongkor), 전국 파일에서 대구 150개 동만 추출 | 2025-04-01 기준 | O |
| 공휴일 | `data/holidays_kr.csv` | 직접 작성 (관공서의 공휴일에 관한 규정, 대체·임시공휴일·선거일 포함) | 2023 ~ 2026 | O |
| 데모 가게 6곳 | `data/processed/demo_stores.csv` | 인허가 실제 점포에 역할 부여, `pipeline/select_demo_stores.py` (화면에는 ID·업종·행정동만 표시) | 2026-09-14 기준 | O |
| 가게 매출 | `data/processed/virtual_sales.csv` | **가상 데이터** (실제 매출 아님), `pipeline/make_virtual_sales.py`. 날씨·축제·휴일·경쟁점 규칙과 가정값은 스크립트 상단에 명시 | 2023-01-01 ~ 2026-09-13, 가게×일×시간대 | O |

용량이 큰 원본(전국 경계 34MB, 인허가 대구 원본 CSV 30MB)은 git에서 제외했습니다. 필요하면 아래에서 받으세요.

```
data/raw/boundary/HangJeongDong_ver20250401.geojson
  https://raw.githubusercontent.com/vuski/admdongkor/master/ver20250401/HangJeongDong_ver20250401.geojson
data/raw/licenses/식품_일반음식점_대구광역시.csv
  https://www.data.go.kr/data/15045016/fileData.do (매일 갱신, cp949)
```

API 키는 프로젝트 루트 `.env`에 넣습니다 (git 제외).

```
DATA_GO_KR_KEY=...      # 공공데이터포털 (get_weather.py, fetch_festival.py)
KAMIS_CERT_KEY=...      # KAMIS (fetch_kamis.py)
KAMIS_CERT_ID=...
NVIDIA_API_KEY=nvapi-...  # NVIDIA Build LLM (make_advice.py)
NVIDIA_MODEL=...        # 선택, 기본 nvidia/nemotron-3-super-120b-a12b
```

키는 코드에 직접 쓰지 않습니다. 모든 스크립트가 프로젝트 루트에서 실행할 때 `.env`를 읽습니다.
