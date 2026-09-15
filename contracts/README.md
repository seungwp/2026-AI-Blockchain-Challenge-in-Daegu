# 데이터 계약 (pipeline → web)

웹 화면이 읽는 JSON 형식입니다. **pipeline(Claude)이 만들고 web(Codex)이 읽기만** 합니다.
형식을 바꾸려면 PR로 합의한 뒤 이 문서와 `sample/`을 함께 수정합니다.

- 생성: `py -X utf8 pipeline/export_web_data.py` (API 키 불필요, 기존 결과 파일만 읽음)
- 인코딩 UTF-8, 날짜 `YYYY-MM-DD`, 금액 단위 원(정수), 비율은 **0~1 소수**(예: 0.161 = 16.1%)
- 단, `advice/` 파일 안의 `signals`·`advice` 문장은 LLM 입력·출력 원본이라 키가 한글이고 `_퍼센트` 필드는 **이미 % 단위 정수**
- 매출은 **가상 데이터**(`is_virtual: true`) → 화면에 "가상 매출 데모" 표시 필수
- 실존 가게 식별 정보(상호·관리번호·정확 좌표)는 포함하지 않음. 지도는 `dong_center`(동네 중심점)만 사용

## 파일 목록
```
sample/
├─ index.json                  기준일 목록, 가게 ID 목록
├─ stores.json                 데모 가게 6곳 (로그인 대상)
├─ advice/<기준일>/<가게ID>.json 이번 주 조언 + 근거 신호 + 검증 결과
├─ sales/<가게ID>.json          최근 90일 일별 매출 (가상)
├─ prices.json                 식자재 8품목 최근 90일 가격 + 최신 급등 확률 (실제 KAMIS)
└─ validation.json             모델·LLM 검증 수치 (신뢰도 화면)
```

## index.json
| 필드 | 타입 | 설명 |
|---|---|---|
| `advice_dates` | string[] | 조언이 있는 기준일 (`2026-06-29`, `2026-09-14`) |
| `live_date` | string | 실시간 모드 기준일 (기상청 예보 사용) |
| `replay_dates` | string[] | 과거 재현 기준일 (실측 날씨, `actual_after` 있음) |
| `store_ids` | string[] | `S1`~`S6` |
| `sales_last_date` | string | 매출 데이터 마지막 날 |

## stores.json (배열)
| 필드 | 예시 | 설명 |
|---|---|---|
| `id` | `S1` | 가게 ID (모든 파일의 키) |
| `login_id` | `demo-s1` | 데모 로그인 ID |
| `category` | `치킨` | 화면 표시 업종 (치킨·한식·고깃집·중국식) |
| `license_category` | `호프/통닭` | 인허가 원본 업태 |
| `gu`, `dong` | `달서구`, `달서구 두류1,2동` | |
| `role`, `scenario` | `치맥페스티벌 인근 치킨집`, `①축제·날씨` | 데모 설명용 |
| `dong_center` | `{lat, lon}` | 동네 중심 좌표 (가게 위치 아님) |

## advice/<기준일>/<가게ID>.json
| 필드 | 설명 |
|---|---|
| `as_of` | 기준일 |
| `mode` | `live` 또는 `replay` |
| `model` | 조언 생성 LLM |
| `advice.summary` | 이번 주 한 문장 요약 |
| `advice.advices[]` | 최대 3개, 중요도 순. `title`, `reason`(근거), `action`(할 일), `urgency`(`높음`/`보통`/`참고`), `evidence`(근거 신호 id 목록) |
| `validation[]` | 생성 시도별 검증. **마지막 원소의 `passed`가 false면 조언을 화면에 표시하지 않고 "이번 주 조언을 준비 중" 상태로 처리** |
| `signals.store` | `id`, `업종`, `동네` |
| `signals.기준일` | 표시용 날짜 문자열 (`6월 29일(월)`) |
| `signals.sales_recent` | `최근28일_일평균매출_원`, `배달비중_퍼센트`, `최근1년_비오는날_배달매출_변화_퍼센트`, `최근1년_금토_매출_변화_퍼센트` |
| `signals.weather_next7` | `source`(출처 문구), `days[]`: `date`, `최고기온`, `최저기온`, `강수량_mm`, (live만) `강수확률_최대`. live는 예보 제공 날짜까지만 있음 |
| `signals.festivals_next7[]` | `id`, `이름`, `기간`, `거리_m` (2km 이내). 빈 배열 가능 |
| `signals.holidays_next14[]` | `날짜`, `이름`. 빈 배열 가능 |
| `signals.ingredients[]` | `id`, `품목`, `현재가_원`, `다음주_급등확률_퍼센트`, `급등경고`(확률 30% 이상), `평년대비_퍼센트` |
| `signals.competitors_90d[]` | `id`, `개업일`, `업종`, `거리_m`(500m 이내), `경과일`. 빈 배열 가능 |
| `signals.dong_closure` | `연도`, `우리동네_음식점_폐업률_퍼센트`, `대구평균_폐업률_퍼센트` (없을 수 있음) |
| `actual_after` | replay만. `다음7일_일평균매출_변화_퍼센트`, `다음7일_식자재_주평균가격_변화_퍼센트{품목: %}` → 신뢰도 화면 "조언 뒤 실제 결과" |

## sales/<가게ID>.json
| 필드 | 설명 |
|---|---|
| `store_id`, `is_virtual`(true), `unit`(`원`) | |
| `days[]` | `date`, `hall`, `delivery`, `total`, `orders`, `ingredient_cost`, `is_rain`, `holiday`(이름 또는 null), `fest_mult`(1.0=축제 없음, 1.1/1.25), `n_comp_90d` |

## prices.json
| 필드 | 설명 |
|---|---|
| `as_of`, `region`, `source`, `alert_prob_threshold`(0.3) | |
| `items[]` | `item`, `unit`(예 `1포기`) |
| `items[].latest` | `price`, `spike_prob`(0~1), `spike_alert`, `vs_normal`(평년 대비, 0~1 소수), `change_7d`(주 평균 변화) |
| `items[].series[]` | `date`, `price`, `normal_price`(평년가, null 가능). 채소는 주말·공휴일 날짜 없음 |

## validation.json
| 필드 | 설명 |
|---|---|
| `sales_model` | `note`, `learning_curve[]`(`train_months`, `model`, `baseline`, `floor` 오차 0~1), `per_store[]`, `effects[]`(`factor`, `truth`, `m3`, `m36`) |
| `price_spike_model` | `note`, `overall[]`(`who`=model/momentum, `precision`, `recall`, `f1`, `pr_auc`, `base_rate`), `per_item[]`(`item`, `episodes`, `caught_model`, `caught_momentum`), `learning_curve[]` |
| `advice_llm[]` | `as_of`, `store_id`, `model`, `attempts`, `passed`, `first_try_passed` |
| `figures[]` | 저장소 기준 그래프 PNG 경로 (`docs/figures/...`). 웹에서 쓰려면 빌드 시 복사 |
