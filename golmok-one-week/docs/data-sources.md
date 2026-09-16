# 데이터 출처 (공공데이터·API)

MVP는 모두 Mock Provider로 동작하며, 아래 실제 데이터로 교체할 수 있도록 인터페이스를 분리했습니다.
**API 키는 코드에 넣지 않고 환경변수로만 주입합니다.**

| 용도 | 출처 | URL | 교체 대상 Provider |
|---|---|---|---|
| 가게·상권 | 소상공인시장진흥공단 상가(상권)정보 | https://www.data.go.kr/data/15083033/fileData.do | `StoreSearchProvider`, `CommercialAreaService` |
| 날씨 예보 | 기상청 기상자료개방포털 / 단기예보 | https://data.kma.go.kr/ | `WeatherProvider` |
| 대기질 | 에어코리아 미세먼지 예보 | https://www.airkorea.or.kr/ | `AirQualityProvider` |
| 축제·행사 | 대구광역시 축제 정보, 한국관광공사 행사정보 | https://info.daegu.go.kr/ | `FestivalProvider` |

## 교체 방법

1. `provider` 패키지에 Adapter 클래스를 추가하고 해당 인터페이스를 구현합니다.
   ```java
   @Component
   @ConditionalOnProperty(name = "golmok.providers.weather", havingValue = "kma")
   public class KmaWeatherProvider implements WeatherProvider { ... }
   ```
2. `application.yml` 의 `golmok.providers.weather` 값을 `mock` → `kma` 로 바꿉니다.
3. 키는 환경변수로 주입합니다. (`DATA_GO_KR_KEY` 등, `golmok.keys.*` 로 바인딩)

## 데모 데이터 (H2 seed)

- 가게 8곳 (대구 음식점 5곳 + 두류동 경쟁 상권 예시 3곳)
- 행사 3건 (대구치맥페스티벌·동성로·수성못, 기준일 주간에 걸치도록 상대 날짜로 생성)
- 날씨 7일 (맑음·흐림·비·고온·미세먼지 나쁨 포함)
- 출처 6건, 운영 규칙 28건

모든 데모 데이터는 `isDemoData = true` 로 표시되며 화면에 "데모 데이터" 배지가 노출됩니다.
