# 골목 한 주 — 백엔드

Spring Boot 3.5 · Java 21 · Gradle · JPA · H2 · springdoc(OpenAPI)

## 실행

```bash
./gradlew bootRun                                   # 기본 프로필 local (H2 인메모리)
./gradlew bootRun --args='--spring.profiles.active=mysql'   # MySQL 전환
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 콘솔: http://localhost:8080/h2-console (`jdbc:h2:mem:golmok`, sa / 비밀번호 없음)

## 패키지 구조

```
com.golmok.oneweek
├── config/       CORS, OpenAPI 설정
├── controller/   REST 엔드포인트 (ApiControllers 안에 Health/Store/Menu/Report/Source)
├── dto/          요청·응답 DTO (ReportDtos, MenuDtos, StoreResponse, SourceResponse, ErrorResponse)
├── entity/       Store, AnalysisReport, Source, FestivalEvent, MenuRule, Enums
├── repository/   JPA 리포지토리 5종
├── provider/     외부 데이터 인터페이스(Providers) + Mock 구현 3종
├── rule/         WeeklyGuideRuleEngine (조건 평가 → 권고 생성)
├── service/      StoreService, MenuClassificationService, CommercialAreaService, ReportService, DataSeeder
└── exception/    NotFoundException, GlobalExceptionHandler
```

## 외부 API 교체

`application.yml` 의 값으로 Provider 구현을 바꿉니다.

```yaml
golmok:
  providers:
    weather: mock       # mock | kma
    air-quality: mock   # mock | airkorea
    festival: mock      # mock | tourapi
    store-search: mock  # mock | sbiz
```

새 Adapter 는 `@ConditionalOnProperty(name = "golmok.providers.weather", havingValue = "kma")` 형태로 추가합니다.
**API 키는 코드·yml에 직접 쓰지 않고 환경변수로 주입합니다** (`DATA_GO_KR_KEY` → `golmok.keys.data-go-kr`).

## 설계 원칙

- 매출액·증감률을 계산하지 않습니다. 권고는 "점검/준비/고려" 수준입니다.
- 모든 권고에 `priority`, `confidence`, `sourceIds` 가 포함됩니다.
- 리포트는 생성 시점의 날씨·행사·상권·권고를 JSON 컬럼에 저장하므로, `GET /api/reports/{id}` 는 항상 같은 결과를 돌려줍니다.
