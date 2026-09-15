# 화면 명세 (기능만, 디자인 없음)

휴대폰 세로 화면(360~430px) 기준 반응형. 색·글꼴·아이콘 등 시각 요소는 넣지 않고 **레이아웃·텍스트·기본 HTML 요소만** 사용한다. 외형은 완성 후 디자인팀이 입힌다.
데이터 필드는 `contracts/README.md` 참고.

## 흐름
```
[1 로그인(데모)] → [2 홈: 이번 주 조언] → [3 조언 상세]
                        │ 하단 탭: 조언(2) · 매출(4) · 식자재(5) · 동네(6)
                        │ 상단: 가게 변경(→1), 기준일 전환, 신뢰도(7)
```
- 라우팅: HashRouter (`#/login`, `#/home`, `#/advice/:index`, `#/sales`, `#/prices`, `#/dong`, `#/trust`)
- 로그인한 가게 ID·기준일은 localStorage에 저장, 다음 방문 시 홈으로 바로 이동 (접근 실패 시 로그인 화면)
- 모든 화면 상단에 "가상 매출 데모" 표시 (매출이 나오는 화면)

## 1. 로그인 (데모)
- 서비스 이름, 한 줄 소개
- 입력: 상호/주소 검색, 사업자등록번호 → **비활성** + "출시 시 사업자 인증 제공" 안내
- 데모 가게 목록 6개 (`stores.json`): `id`·`category`·`dong`·`role` → 누르면 로그인 처리 후 홈
- 상태: 없음

## 2. 홈: 이번 주 조언
- 상단: 가게(`S1 치킨집 · 달서구 두류1,2동`), 기준일 선택(`index.advice_dates`, 기본 `live_date`), 모드 표시(live=실시간 예보 / replay=과거 재현)
- 요약: `advice.summary`
- 조언 카드 최대 3개: `urgency`, `title`, `action` → 누르면 3
- 다음 7일 날씨 요약 줄: `weather_next7.days` (날짜, 최고/최저, 강수), `source` 문구
- 상태
  - 검증 실패(`validation` 마지막 `passed=false`): 카드 대신 "이번 주 조언을 준비 중입니다"
  - 조언 1~2개: 있는 만큼만
  - 날씨 예보가 7일 미만(live): 제공된 날짜만 + "예보는 ○월 ○일까지 제공"

## 3. 조언 상세
- `urgency`, `title`, `reason`, `action`
- 근거 데이터: `evidence` id를 신호로 매핑해 표시
  - `festival_*` → 축제 이름·기간·거리 / `price_*` → 품목 현재가·급등확률·평년대비 / `comp_*` → 개업일·업종·거리·경과일
  - `weather`·`weather_next7` → 날씨 표 / `sales_recent` → 매출 패턴 / `closure` → 동네 폐업률 / `holiday`·`holidays_next14` → 공휴일
- 알 수 없는 id는 무시

## 4. 매출 (탭)
- 요약: `sales_recent` 4개 값
- 그래프: `sales/<id>.json` 최근 90일 일별 `hall`·`delivery` (선 또는 막대, 라이브러리 기본 스타일)
- 비 온 날·공휴일·축제일 표시(`is_rain`, `holiday`, `fest_mult>1`) — 텍스트 범례
- "가상 매출 데이터" 문구

## 5. 식자재 (탭)
- 우리 가게 품목만: `advice.signals.ingredients` 기준 (현재가·급등확률·경고·평년대비)
- 급등 경고 있는 품목을 위로. 경고 없으면 "이번 주 급등 경고 없음"
- 품목 선택 시 `prices.json` `series` 가격 그래프(평년가 함께)
- 출처 `prices.source`, 기준일 `prices.as_of`

## 6. 동네 (탭)
- 지도: `stores.dong_center` 중심 핀 1개만 (가게 정확 위치 금지). 지도 라이브러리 로드 실패 시 지도 영역 숨김
- 근처 축제 목록 `festivals_next7` (없으면 "7일 안 근처 축제 없음")
- 최근 경쟁점 개업 `competitors_90d` (없으면 "최근 90일 500m 안 동종 개업 없음")
- 동네 폐업률 `dong_closure` (없으면 섹션 숨김)
- 공휴일 `holidays_next14`

## 7. 신뢰도 (상단 메뉴, 심사위원용)
- 조언 뒤 실제 결과: replay 기준일의 가게별 `actual_after` 표
- 식자재 급등 모델: `price_spike_model.overall`(모델 vs 규칙), `per_item`, `learning_curve`
- 매출 예측 모델: `sales_model.learning_curve`, `per_store`, `effects` (가상 데이터 기준 문구)
- LLM 조언 검증: `advice_llm` 통과 현황
- 데이터 출처 링크 목록 (README 출처 표)

## 완료 기준 (web)
- `npm run build` 성공, `web/dist/index.html` 더블클릭으로 1~7 모든 화면 이동·데이터 표시
- 360px 폭에서 가로 스크롤 없음
- `contracts/sample` JSON을 교체해도 코드 수정 없이 반영 (데이터 접근은 `web/src/data.ts`만)
