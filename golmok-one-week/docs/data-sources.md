# 데이터 출처 (공공데이터·API)

*최종 갱신: 2026-09-17*

**API 키는 코드에 넣지 않고 환경변수로만 주입합니다** (`.env`, `golmok.keys.*`).

## 연동 현황

| 용도 | 출처 | URL | Provider | 상태 |
|---|---|---|---|---|
| 가게·상권 | 행정안전부 식품_일반음식점 인허가 정보 | https://www.data.go.kr/data/15045016/fileData.do | `StoreSearchProvider`, `CommercialAreaService` | **연동 완료 ✅** (24,079곳 실데이터, `pipeline/export_golmok_stores.py`로 시드) |
| 날씨 예보 | 기상청 단기예보·중기예보 | https://data.kma.go.kr/ | `KmaWeatherProvider` | **연동 완료 ✅** (0~4일 동네 5km 격자, 5~6일 대구 전역) |
| 명절 | 한국천문연구원 특일 정보 | https://www.data.go.kr/data/15012690/openapi.do | `KasiHolidayProvider` | **연동 완료 ✅** (추석 전날·당일·연휴기간·마지막날만, 설날 등은 계수 없어 미분류) |
| 축제·행사 | 한국관광공사 TourAPI | https://api.visitkorea.or.kr/ | `MockFestivalProvider` (실데이터를 CSV로 시드) | **실데이터 ✅** (`pipeline/fetch_festival.py`로 수집한 17건, 요청 시 실시간 호출은 아님) |
| 식자재 가격 | KAMIS 농산물유통정보 | https://www.kamis.or.kr/ | `KamisPriceProvider` | **연동 완료 ✅** (가격은 요청 시 실시간 조회, 급등확률은 `pipeline/price_spike_model.py`의 배치 스냅샷) |
| 미세먼지 | ~~에어코리아~~ | — | — | **제거됨** — 미세먼지가 매출과 유의한 관계가 없다는 근거(성은영 2017, `docs/coefficients.md`)로 조건·필드 전체 삭제 |

교체 방법(아직 Mock인 부분에 적용):
```java
@Component
@ConditionalOnProperty(name = "golmok.providers.weather", havingValue = "kma")
public class KmaWeatherProvider implements WeatherProvider { ... }
```
`application.yml`의 `golmok.providers.*` 값을 바꾸면 Mock ↔ 실 Provider가 전환됩니다.

## 시드 데이터 (H2, `DataSeeder` + `resources/data/*.csv`)

| 데이터 | 건수 | 원본 |
|---|---|---|
| 가게 | 24,079곳 | `daegu_stores.csv` — 행정안전부 인허가, 영업 중만 |
| 축제 | 17건 | `daegu_festivals.csv` — TourAPI |
| 출처 | 14건 | `sources.csv` — `docs/coefficients.md` 출처 번호와 고정 매핑 |
| 운영 규칙 | 37건 | `menu_rules.csv` |
| 식자재 가격 스냅샷 | 8품목 | `ingredient_prices.csv` — 요청 시 KAMIS 실시간 값으로 대체됨 |

CSV만 고치면 코드 재컴파일 없이 문구·데이터를 바꿀 수 있습니다(Java에 하드코딩된 시드 없음).

사용자가 검색 결과 없이 주소를 직접 입력한 가게만 `isDemoData: true`로 남습니다. 그 밖의 가게·날씨(0~4일)·축제·상권·식자재 가격은 기본적으로 실데이터입니다.
