# API 명세

*최종 갱신: 2026-09-17*

- Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 모든 요청·응답 인코딩: UTF-8
- CORS 허용 origin: `application.yml`의 `golmok.cors-allowed-origins` (기본 `http://localhost:3000`)

## 1. GET `/api/health`
```json
{ "status": "ok" }
```

## 2. GET `/api/stores/search?keyword={keyword}&city=대구광역시`
상호명 또는 주소 부분 일치 검색. **결과 최대 20건**(서버에서 상한, 대구 전역 음식점 24,000여 곳 중 상위 20건만 반환).
```json
[
  {
    "id": 2385,
    "name": "교촌치킨 두류점",
    "category": "호프/통닭",
    "address": "대구광역시 달서구 두류동 620-12",
    "roadAddress": "대구광역시 달서구 ...",
    "latitude": 35.8521,
    "longitude": 128.55481,
    "city": "대구광역시",
    "district": "달서구",
    "isDemoData": false
  }
]
```
가게 데이터는 식품의약품안전처 일반음식점 인허가 실데이터입니다(영업 중인 곳만, 24,079곳). `isDemoData: true`는 사용자가 검색 결과 없이 주소를 직접 입력해 만든 임시 가게뿐입니다.

## 3. GET `/api/stores/{storeId}`
가게 단건 조회. 없으면 404 `NOT_FOUND`.

## 4. POST `/api/stores/manual`
검색 결과가 없을 때 주소 직접 입력으로 가게 생성 (201).
```json
{ "address": "대구광역시 중구 동성로 1", "city": "대구광역시" }
```

## 5. POST `/api/menu/classify`
```json
{ "menuName": "닭똥집", "storeCategory": "음식점 > 한식 > 닭요리" }
```
```json
{ "menuCategory": "구이", "confidence": "HIGH", "matchedKeywords": ["닭똥집"], "isDemoData": true }
```
매칭 실패 시 `{"menuCategory": "기타", "confidence": "LOW", "matchedKeywords": []}`.

## 6. POST `/api/reports` (201)
```json
{ "storeId": 2385, "mainMenu": "후라이드 치킨", "menuCategory": "치킨" }
```
`menuCategory`를 생략하면 서버가 자동 분류합니다.

응답(전체 필드):
```json
{
  "reportId": 1,
  "store": { "id": 2385, "name": "교촌치킨 두류점", "...": "..." },
  "mainMenu": "후라이드 치킨",
  "menuCategory": "치킨",
  "analysisStartDate": "2026-09-17",
  "analysisEndDate": "2026-09-23",
  "summary": "이번 주는 경쟁 강도 높음 조건입니다. 아래 점검 항목은 운영 참고용이며 ...",
  "topActions": [
    {
      "title": "주말 · 인력·운영 점검",
      "text": "주말은 피크 시간대 인력과 재료를 사전에 점검하는 것을 권장합니다.",
      "type": "STAFFING",
      "priority": "MEDIUM",
      "confidence": "HIGH",
      "conditionType": "WEEKEND",
      "basis": "2026-09-19(토) 주말",
      "date": "2026-09-19",
      "sourceIds": [4]
    }
  ],
  "weather": [
    {
      "date": "2026-09-17", "dayOfWeek": "목", "condition": "맑음",
      "tempMax": 27.0, "tempMin": 18.0,
      "precipitationProbability": 10, "precipitationMm": 0.0,
      "humidity": 55,
      "isDemoData": false, "sourceId": 6
    }
  ],
  "commercialArea": {
    "district": "달서구", "dong": "두류동",
    "totalStores": 179, "sameCategoryStores": 25, "competitionLevel": "높음",
    "note": "반경 500m 기준 음식점 179곳, 유사 업종 25곳으로 경쟁 강도는 '높음' 수준입니다. 운영 참고용 집계입니다.",
    "isDemoData": false, "sourceIds": [1]
  },
  "festivals": [
    {
      "id": 3, "name": "대구메이커페스타",
      "startDate": "2026-09-19", "endDate": "2026-09-20",
      "locationName": "북구 침산2동", "address": "대구광역시 북구 ...",
      "latitude": 35.88, "longitude": 128.59,
      "distanceMeters": 1224, "impactLevel": "간접 영향 가능",
      "impactNote": "가게에서 다소 떨어진 곳에서 행사가 열립니다. ...",
      "isDemoData": false, "sourceId": 13
    }
  ],
  "ingredientPrices": [
    {
      "item": "닭", "unit": "1kg", "price": 4269.0, "priceDate": "2026-09-16",
      "probSpike": 0.0127, "alert": false, "vsNormalRatio": -0.2412,
      "isDemoData": false, "sourceId": 14
    }
  ],
  "dailyGuides": [
    { "date": "2026-09-17", "dayOfWeek": "목", "weatherSummary": "맑음 · 최고 27.0℃ / 최저 18.0℃ · 강수확률 10%", "guides": [] }
  ],
  "sources": [
    {
      "id": 1, "sourceType": "PUBLIC_DATA", "title": "소상공인시장진흥공단 상가(상권)정보",
      "organization": "소상공인시장진흥공단", "publicationYear": null,
      "url": "https://www.data.go.kr/data/15083033/fileData.do",
      "description": "...", "reliabilityNote": "..."
    }
  ],
  "isDemoData": false,
  "demoNotice": null,
  "disclaimer": "본 결과는 공공데이터 및 연구자료를 기반으로 한 운영 참고용 제안이며, 실제 매출을 보장하지 않습니다."
}
```

### 필드 참고

- **`weather[].humidity`**: 0~4일차는 기상청 단기예보(동네 5km 격자) 값, 5~6일차는 중기예보(대구 전역) 구간이라 **`null`**입니다.
- **`weather[].pm10Grade`는 존재하지 않습니다.** 미세먼지 조건은 근거 부족으로 전면 제거했습니다.
- **`ingredientPrices`**: 메뉴 카테고리에 대응하는 KAMIS 품목이 없으면 **빈 배열**입니다(냉면류·일식·양식·기타·공통). `price`·`priceDate`는 요청 시점에 KAMIS API를 실시간 조회한 값(실패 시 최근 스냅샷으로 대체), `probSpike`·`alert`는 매일 갱신되는 예측 모델의 배치 스냅샷입니다.
- **`conditionType`** 가능한 값: `RAIN`, `HOT`, `COLD`, `WEEKEND`, `HOLIDAY`, `FESTIVAL`, `COMPETITION`, `PRICE_SPIKE`. `HOLIDAY`의 `basis`엔 "추석 전날"/"추석 당일"/"추석 연휴 기간"/"추석 연휴 마지막날" 라벨이 들어갑니다.
- **`isDemoData`(최상위)**: 가게·날씨 각 날짜·축제·상권·식자재 가격 중 **하나라도** `isDemoData: true`면 전체가 `true`입니다. 전부 실데이터면 `false`이고 `demoNotice`는 `null`입니다.

## 7. GET `/api/reports/{reportId}`
저장된 리포트를 그대로 재조회합니다. 생성 응답과 동일한 JSON입니다.

## 8. GET `/api/sources`, GET `/api/sources/{sourceId}`
출처 전체(14건)/단건 조회. ID는 `docs/coefficients.md`의 출처 번호와 1:1로 고정되어 있습니다.

## 에러 응답
```json
{
  "timestamp": "2026-09-17T12:00:00",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "상호명 또는 주소를 입력해주세요."
}
```
| 상황 | status | code |
|---|---|---|
| 필수 값 누락·형식 오류 | 400 | INVALID_REQUEST |
| JSON 파싱·인코딩 오류, 잘못된 enum 값 | 400 | INVALID_REQUEST |
| 없는 리소스 | 404 | NOT_FOUND |
| 서버 오류 | 500 | INTERNAL_ERROR |
