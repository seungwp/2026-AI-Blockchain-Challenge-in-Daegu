# API 명세

- Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 모든 요청·응답 인코딩: UTF-8

## 1. GET `/api/health`
```json
{ "status": "ok" }
```

## 2. GET `/api/stores/search?keyword={keyword}&city=대구광역시`
상호명 또는 주소 부분 일치 검색.
```json
[
  {
    "id": 1,
    "name": "평화시장 닭똥집",
    "category": "음식점 > 한식 > 닭요리",
    "address": "대구광역시 동구 신암동",
    "roadAddress": "대구광역시 동구 아양로9길",
    "latitude": 35.8817,
    "longitude": 128.6215,
    "city": "대구광역시",
    "district": "동구",
    "isDemoData": true
  }
]
```

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
{ "storeId": 5, "mainMenu": "치킨", "menuCategory": "치킨" }
```
`menuCategory`를 생략하면 서버가 자동 분류합니다.

응답(주요 필드):
```json
{
  "reportId": 1,
  "store": { "id": 5, "name": "두류공원 치킨", "...": "..." },
  "mainMenu": "치킨",
  "menuCategory": "치킨",
  "analysisStartDate": "2026-09-16",
  "analysisEndDate": "2026-09-22",
  "summary": "이번 주는 비 예보 2일, ... 운영 참고용이며 ...",
  "topActions": [
    {
      "title": "비 예보 · 포장·배달 점검",
      "text": "비 예보가 있는 날은 포장·배달 가능 여부와 포장 용기 재고를 점검하는 것을 권장합니다.",
      "type": "DELIVERY",
      "priority": "HIGH",
      "confidence": "HIGH",
      "conditionType": "RAIN",
      "basis": "2026-09-18(금) 강수확률 80%",
      "date": "2026-09-18",
      "sourceIds": [4]
    }
  ],
  "weather": [
    {
      "date": "2026-09-16", "dayOfWeek": "수", "condition": "맑음",
      "tempMax": 27.0, "tempMin": 18.0,
      "precipitationProbability": 10, "precipitationMm": 0.0,
      "pm10Grade": "보통", "isDemoData": true, "sourceId": 6
    }
  ],
  "commercialArea": {
    "district": "달서구", "dong": "두류동",
    "totalStores": 3, "sameCategoryStores": 2, "competitionLevel": "보통",
    "note": "반경 500m 기준 ...", "isDemoData": true, "sourceIds": [1]
  },
  "festivals": [
    {
      "id": 1, "name": "대구치맥페스티벌 (데모)",
      "startDate": "2026-09-18", "endDate": "2026-09-21",
      "locationName": "두류공원", "address": "대구광역시 달서구 공원순환로 36",
      "latitude": 35.8509, "longitude": 128.5588,
      "distanceMeters": 245, "impactLevel": "직접 영향 가능",
      "impactNote": "행사장과 가까워 ...", "isDemoData": true, "sourceId": 5
    }
  ],
  "dailyGuides": [
    { "date": "2026-09-16", "dayOfWeek": "수", "weatherSummary": "맑음 · 최고 27.0℃ ...", "guides": [] }
  ],
  "sources": [
    {
      "id": 1, "sourceType": "PUBLIC_DATA", "title": "소상공인시장진흥공단 상가(상권)정보",
      "organization": "소상공인시장진흥공단", "publicationYear": null,
      "url": "https://www.data.go.kr/data/15083033/fileData.do",
      "description": "...", "reliabilityNote": "..."
    }
  ],
  "isDemoData": true,
  "demoNotice": "현재 일부 데이터는 데모 데이터입니다. ...",
  "disclaimer": "본 결과는 공공데이터 및 연구자료를 기반으로 한 운영 참고용 제안이며, 실제 매출을 보장하지 않습니다."
}
```

## 7. GET `/api/reports/{reportId}`
저장된 리포트를 그대로 재조회합니다. 생성 응답과 동일한 JSON입니다.

## 8. GET `/api/sources`, GET `/api/sources/{sourceId}`
출처 전체/단건 조회.

## 에러 응답
```json
{
  "timestamp": "2026-09-16T12:00:00",
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
