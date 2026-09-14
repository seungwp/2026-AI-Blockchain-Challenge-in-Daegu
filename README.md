# 2026 AI Blockchain Challenge in Daegu

## 데이터 출처

모든 데이터는 공공데이터이며, 이용조건은 각 출처 페이지를 따릅니다.

| 데이터 | 경로 | 출처 | 기간·범위 | git |
|---|---|---|---|---|
| 대구 과거 날씨 (ASOS 일자료) | `data/raw/weather/` | 기상청 기상자료개방포털 (https://data.kma.go.kr), 대구 관측소 143 | 2023-01-01 ~ 2026-09-13 | O |
| 대구 날씨 예보 (단기예보) | `data/raw/forecast/` | 공공데이터포털 기상청_단기예보 조회서비스 (https://www.data.go.kr), 격자 nx=89, ny=90 | 2026-09-14 05시 발표분 | O |
| 대구 문화축제 | `data/raw/festival/` | 공공데이터포털 대구광역시_문화축제 (https://www.data.go.kr/data/15130238/fileData.do), 공공누리 제4유형(출처표시·상업적 이용금지·변경금지) | 2024년 32건 | O |
| 농축수산물 품목·등급 코드표 | `data/raw/kamis/` | KAMIS 농산물유통정보 Open-API (https://www.kamis.or.kr) | - | O |
| 대구 소매가격 (식자재 8품목) | `data/raw/kamis/daegu_retail_daily.csv` | KAMIS Open-API `periodRetailProductList`, 지역코드 2200(대구), `analysis/fetch_kamis.py` | 2023-01-01 ~ 2026-09-13 | O |
| 일반음식점 인허가 | `data/raw/licenses/` | D-데이터허브 (https://data.daegu.go.kr) DMI_0000119348 | 2026년 7월 기준 | X (25MB) |
| 행정동 경계 | `data/raw/boundary/` | vuski/admdongkor `ver20250401` (https://github.com/vuski/admdongkor) | 2025-04-01 기준 | X (34MB) |
| 공휴일 | `data/holidays_kr.csv` | 직접 작성 (관공서의 공휴일에 관한 규정, 대체·임시공휴일·선거일 포함) | 2023 ~ 2026 | O |
| 가게 매출 | - | **가상 데이터** (실제 매출 아님, 생성 규칙은 `docs/데이터관리_데모용.xlsx` 가상매출 시트) | 2023 ~ 2026 | 생성 예정 |

git에 없는 파일은 위 출처에서 받아 같은 경로에 넣으세요.

```
data/raw/boundary/HangJeongDong_ver20250401.geojson
  https://raw.githubusercontent.com/vuski/admdongkor/master/ver20250401/HangJeongDong_ver20250401.geojson
data/raw/licenses/6270000-대구광역시-07-24-04-P-일반음식점.xlsx
  D-데이터허브 → 인허가데이터 식품_음식점
```

API 키는 프로젝트 루트 `.env`에 넣습니다 (git 제외).

```
DATA_GO_KR_KEY=...
KAMIS_CERT_KEY=...
KAMIS_CERT_ID=...
```
