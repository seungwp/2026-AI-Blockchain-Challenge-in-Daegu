# API 명세

*최종 갱신: 2026-09-18*

## 2026-09-18 추가 필드 및 주소 검색

기존 필드는 유지한다. 수집·갱신 방법은 [공공데이터 연결 현황](public-data-integration.md) 참고.

- `GET /api/addresses/search?keyword=대구광역시 중구 공평로 88`: `[{roadAddress,address,district,latitude,longitude}]`.
  네이버 Maps Geocoding API의 대구 결과만 반환하며, 좌표는 WGS84 위도·경도다.
- `POST /api/stores/manual`: 실제 주소를 확인한 뒤 생성한다. 모호하거나 없는 주소는 400.
  네이버 지오코딩으로 주소 좌표를 확인한 뒤 생성한다.
- `commercialArea.note`: 세부 업종이 연결된 경우 기준월·분류 확인 수를 표시하고 `sourceIds`에 15를 추가한다.
  화면은 반경 500m 음식점 수와 유사 업종 수만 표시한다.
- `festivals[]` 추가: `playTime`, `fee`, `contact`(문자열 또는 null), `fetchedAt`(확인일 또는 null).
- `ingredientPrices[]` 추가: `history: [{date,price}]`, `comparisonDate`, `vsPreviousWeekRatio`,
  `predictionDate`, `predictionStale`, `priceBasis`. 비교 비율 0.1은 +10%이며 계산은 서버에서 한다.
  예측이 오래되면 `probSpike=null`, `alert=false`; 이는 가격 안정 판정이 아닌 예측 갱신 대기다.
- 출처는 기존 14건에서 15건으로 늘었다. 출처 15는 상가정보이며 관리번호를 응답하지 않는다.
- 프론트는 4xx 오류를 데모로 대체하지 않는다. 기존 데모 폴백은 연결/서버 실패 시 유지한다.

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
가게 데이터는 행정안전부 식품_일반음식점 인허가 실데이터입니다(영업 중인 곳만, 24,079곳). `isDemoData: true`는 사용자가 검색 결과 없이 주소를 직접 입력해 만든 임시 가게뿐입니다.

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
  "summary": "이번 주는 인근 행사 '대구메이커페스타' 조건이 있습니다. 해당 날짜의 점검 항목을 확인해보세요.",
  "aiSummary": "이번 주는 토요일 비 예보와 무더운 날씨가 겹쳐 있어요. 포장·배달 준비를 미리 해두시면 좋겠습니다.",
  "topActions": [
    {
      "title": "행사 · 고객 안내",
      "text": "인근 행사 기간에는 행사장 거리와 교통 혼잡을 함께 고려해 매장·배달 운영 계획을 점검해보세요.",
      "type": "NOTICE",
      "priority": "LOW",
      "confidence": "MEDIUM",
      "conditionType": "FESTIVAL",
      "basis": "대구메이커페스타 · 9/19(토)~9/20(일) 개최 · 가게에서 1.2km",
      "date": "2026-09-19",
      "sourceIds": [5]
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
    "totalStores": 179, "sameCategoryStores": 25,
    "note": "반경 500m 기준 음식점 179곳, 유사 업종 25곳입니다. 운영 참고용 집계입니다.",
    "isDemoData": false, "sourceIds": [1]
  },
  "festivals": [
    {
      "id": 3, "name": "대구메이커페스타",
      "startDate": "2026-09-19", "endDate": "2026-09-20",
      "locationName": "북구 침산2동", "address": "대구광역시 북구 ...",
      "latitude": 35.88, "longitude": 128.59,
      "distanceMeters": 1224, "impactLevel": "간접 영향 가능",
      "impactNote": "행사장과 다소 떨어져 있어 간접적인 유동 변화 가능성이 있습니다. (중간 신뢰도)",
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
      "id": 1, "sourceType": "PUBLIC_DATA", "title": "행정안전부 식품_일반음식점 인허가 정보",
      "organization": "행정안전부", "publicationYear": null,
      "url": "https://www.data.go.kr/data/15045016/fileData.do",
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
- **`ingredientPrices`**: 메뉴에 연결된 품목이 없으면 빈 배열입니다. 수집 당일은 검증된 가격 스냅샷, 이후는 실 API(실패 시 스냅샷)를 사용합니다. 예측 모델은 별도 배치 스냅샷이며 자동 갱신되지 않습니다. 가격·예측 기준일과 오래된 예측 숨김 정책은 위 추가 필드를 참고하세요.
- **`festivals`**: 가게에서 **3km 이내** 행사만 들어갑니다(1km 이내 `직접 영향 가능`, 3km 이내 `간접 영향 가능`). 가까운 행사가 없으면 빈 배열입니다.
- **`topActions`**: **최대 3개**. 같은 조건·유형은 한 번만 담고, 비·명절·행사·가격처럼 이번 주만의 조건이 있으면 매주 반복되는 주말 권고는 제외합니다.
- **`conditionType`** 가능한 값: `RAIN`, `HOT`, `COLD`, `WEEKEND`, `HOLIDAY`, `FESTIVAL`, `COMPETITION`, `PRICE_SPIKE`. `HOLIDAY`의 `basis`엔 "추석 전날"/"추석 당일"/"추석 연휴 기간"/"추석 연휴 마지막날" 라벨이 들어갑니다.
- **`aiSummary`**: Groq LLM이 `summary`와 같은 사실을 재료로 다듬은 자연스러운 문장. 항상 **null일 수 있음**(키 없음·API 실패·타임아웃·검증 실패 시). null이어도 `summary`가 있으니 화면은 항상 완전함. 프론트는 `{report.aiSummary && <p className="ai-summary">...}`처럼 있을 때만 보여주면 됨
- **`isDemoData`(최상위)**: 가게·날씨 각 날짜·축제·상권·식자재 가격 중 **하나라도** `isDemoData: true`면 전체가 `true`입니다. 전부 실데이터면 `false`이고 `demoNotice`는 `null`입니다.

## 7. GET `/api/reports/{reportId}`
저장된 리포트를 그대로 재조회합니다. 생성 응답과 동일한 JSON입니다.

## 8. GET `/api/sources`, GET `/api/sources/{sourceId}`
출처 전체(14건)/단건 조회. ID는 `docs/coefficients.md`의 출처 번호와 1:1로 고정되어 있습니다.

## 9. POST `/api/reports/{reportId}/chat`
리포트 화면 우측 하단 챗봇. **이 리포트에 있는 사실만** 근거로 답하는 Q&A이며, 대구 상권 전반을 다루는 일반 챗봇이 아닙니다.

요청:
```json
{ "question": "이번 주에 비 오는 날이 언제예요?", "history": [{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}] }
```
- `question`: 필수, 최대 300자
- `history`: 선택, 이전 대화(최근 6개만 사용). `role`은 `user`/`assistant`만 신뢰하고 그 외 값은 서버에서 `user`로 강등함

응답:
```json
{ "answer": "이번 주에는 9월 17일(목)과 9월 18일(금)에 비가 올 확률이 30%로 흐린 날씨가 예상됩니다..." }
```

**절대 규칙**: 매출 증감을 퍼센트·금액으로 확정해 말하지 않습니다. 매출 예측을 요청받으면 "정확한 매출 예측은 어렵습니다"라고 답하고 리포트의 참고 자료를 안내합니다. 리포트 데이터에 없는 숫자·날짜·사실은 만들어내지 않으며, 검증에 실패하면 한 번 재시도 후에도 실패하면 "답변을 가져오지 못했습니다" 메시지를 돌려줍니다. LLM 키가 없으면(`GROQ_API_KEY` 미설정) 항상 안내 문구만 돌려줍니다.

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
