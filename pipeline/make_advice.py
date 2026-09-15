"""가게별 '이번 주 조언' 생성: 신호 정리 → LLM(NVIDIA Build) 문장 생성 → 숫자·누락 검증 → 저장.

usage: py -X utf8 make_advice.py 2026-06-29 2026-09-14

기준일마다 data/processed/advice/<기준일>/S1.json ... 저장 (심사위원은 API 키 없이 저장본으로 시연)
- 2026-09-14: 실시간 모드. 기상청 단기예보(9/14 05시 발표) 사용
- 그 외 날짜: 과거 재현 모드. 다음 7일 날씨는 실측값을 예보 대신 사용, 조언 뒤 실제 결과(actual_after)도 기록
LLM은 계산하지 않음: 모든 숫자는 signals에서 오고, 검증에서 signals에 없는 숫자가 나오면 재생성
"""
import json
import re
import sys
from pathlib import Path
from typing import Literal

import numpy as np
import openai
import pandas as pd
from pydantic import BaseModel, Field, ValidationError
LIVE_DATE = pd.Timestamp("2026-09-14")
# 모델 평가용 임계값(0.08, F1 최대)은 오경보가 많아 조언에는 부적합 → 사장님 알림은 확률 30% 이상만
# (2025 하반기 검증: 0.08이면 적중 31%·포착 73%, 0.30이면 적중 40%·포착 30%)
ADVICE_ALERT_PROB = 0.30
OUT = Path("data/processed/advice")
FIG = Path("docs/figures")
WD = "월화수목금토일"
USAGE_ITEMS = {"치킨": ["닭", "무", "대파"], "한식": ["배추", "무", "양파", "대파", "깐마늘", "계란"],
               "고깃집": ["삼겹살", "배추", "양파", "대파", "깐마늘"], "중국식": ["양파", "대파", "깐마늘", "계란", "삼겹살"]}
GROUP = {"통닭(치킨)": "치킨", "호프/통닭": "치킨", "한식": "한식", "식육(숯불구이)": "고깃집", "분식": "분식", "중국식": "중국식"}

env = dict(l.strip().split("=", 1) for l in open(".env", encoding="utf-8-sig") if "=" in l and not l.startswith("#"))
# NVIDIA Build는 OpenAI 호환 API. 모델은 .env의 NVIDIA_MODEL로 바꿀 수 있음
# 무료 등급은 대기열 때문에 첫 응답까지 2~3분 걸릴 수 있어 timeout을 넉넉히
client = openai.OpenAI(base_url="https://integrate.api.nvidia.com/v1", api_key=env["NVIDIA_API_KEY"], max_retries=8, timeout=600)  # 무료 서버 503 과부하가 잦음
MODEL = env.get("NVIDIA_MODEL", "nvidia/nemotron-3-super-120b-a12b")  # DeepSeek V4 Flash는 무료 대기열로 요청당 2~3분

stores = pd.read_csv("data/processed/demo_stores.csv")
sales = pd.read_csv("data/processed/virtual_sales.csv", parse_dates=["date"])
sales["total"] = sales["sales_hall"] + sales["sales_delivery"]
w = pd.read_csv("data/raw/weather/daegu_asos143_daily_20230101_20260913.csv", encoding="cp949", parse_dates=["일시"]).set_index("일시")
fc = pd.read_csv("data/raw/forecast/daegu_weather_forecast.csv", dtype=str)
fests = pd.read_csv("data/raw/festival/daegu_festival_tourapi.csv", parse_dates=["start", "end"])
hol = pd.read_csv("data/holidays_kr.csv", parse_dates=["date"])
spike_hist = pd.read_csv("data/processed/price_spike_predictions_2026.csv", parse_dates=["date"])
spike_latest = pd.read_csv("data/processed/price_spike_latest.csv", parse_dates=["date"])
kamis = pd.read_csv("data/raw/kamis/daegu_retail_daily.csv", parse_dates=["date"])
closure = pd.read_csv("data/processed/dong_year_closure.csv", dtype={"year": str})
lic = pd.read_csv("data/processed/stores_with_dong.csv", dtype=str)
lic["open"], lic["close"] = pd.to_datetime(lic["open"]), pd.to_datetime(lic["close"])
lic["x"], lic["y"] = pd.to_numeric(lic["좌표정보X(EPSG5174)"]), pd.to_numeric(lic["좌표정보Y(EPSG5174)"])
lic["group"] = lic["업태구분명"].map(GROUP)
lic = lic[~(lic["소재지전체주소"].str.contains("일원|축제", na=False) | ((lic["close"] - lic["open"]).dt.days < 30)) & lic["x"].notna()]
from pyproj import Transformer  # noqa: E402

fests["x"], fests["y"] = Transformer.from_crs("EPSG:4326", "EPSG:5174", always_xy=True).transform(fests["lon"].values, fests["lat"].values)


def md(ts):
    return f"{ts.month}월 {ts.day}일({WD[ts.dayofweek]})"


def weather_next7(as_of):
    days = pd.date_range(as_of + pd.Timedelta(days=1), periods=7)
    if as_of == LIVE_DATE:
        rows = []
        for dt in days:
            f = fc[fc["fcstDate"] == dt.strftime("%Y%m%d")]
            if f.empty:
                continue
            pcp = pd.to_numeric(f.loc[f["category"] == "PCP", "fcstValue"].str.extract(r"([\d.]+)")[0], errors="coerce").fillna(0)
            val = lambda c: pd.to_numeric(f.loc[f["category"] == c, "fcstValue"], errors="coerce")
            rows.append(dict(date=md(dt), 최고기온=float(val("TMX").max()) if val("TMX").notna().any() else float(val("TMP").max()),
                             최저기온=float(val("TMN").min()) if val("TMN").notna().any() else float(val("TMP").min()),
                             강수확률_최대=int(val("POP").max()), 강수량_mm=round(float(pcp.sum()), 1)))
        last = md(pd.Timestamp(fc["fcstDate"].max()))
        return dict(source=f"기상청 단기예보(9월 14일 05시 발표, {last}까지만 제공)", days=rows)
    rows = [dict(date=md(dt), 최고기온=float(w.loc[dt, "최고기온(°C)"]), 최저기온=float(w.loc[dt, "최저기온(°C)"]),
                 강수량_mm=float(np.nan_to_num(w.loc[dt, "일강수량(mm)"]))) for dt in days]
    return dict(source="과거 재현: 실제 관측값을 예보 대신 사용", days=rows)


def signals_for(s, as_of):
    xy = np.array([s.x, s.y])
    hist = sales[(sales["store_id"] == s.store_id) & (sales["date"] <= as_of)]
    daily = hist.groupby("date").agg(total=("total", "sum"), deliv=("sales_delivery", "sum"), rain=("is_rain", "first"))
    last28 = daily.iloc[-28:]
    year = daily.iloc[-365:]
    deliv_rain = year.loc[year["rain"], "deliv"].mean() / year.loc[~year["rain"], "deliv"].mean() - 1
    wknd = daily.index.dayofweek.isin([4, 5])
    sig = {
        "store": dict(id=s.store_id, 업종=s.group, 동네=s.adm_nm),
        "기준일": md(as_of),
        "sales_recent": dict(최근28일_일평균매출_원=int(round(last28["total"].mean(), -3)),
                             배달비중_퍼센트=round(last28["deliv"].sum() / last28["total"].sum() * 100),
                             최근1년_비오는날_배달매출_변화_퍼센트=round(deliv_rain * 100),
                             최근1년_금토_매출_변화_퍼센트=round((daily.loc[wknd, "total"].mean() / daily.loc[~wknd, "total"].mean() - 1) * 100)),
        "weather_next7": weather_next7(as_of),
        "festivals_next7": [], "holidays_next14": [], "ingredients": [], "competitors_90d": [],
    }
    win_s, win_e = as_of + pd.Timedelta(days=1), as_of + pd.Timedelta(days=7)
    for f in fests[(fests["start"] <= win_e) & (fests["end"] >= win_s)].itertuples():
        dist = int(np.hypot(f.x - xy[0], f.y - xy[1]))
        if dist <= 2000:
            sig["festivals_next7"].append(dict(id=f"festival_{f.contentid}", 이름=f.title, 기간=f"{md(f.start)}~{md(f.end)}", 거리_m=dist))
    for h in hol[(hol["date"] > as_of) & (hol["date"] <= as_of + pd.Timedelta(days=14))].itertuples():
        sig["holidays_next14"].append(dict(날짜=md(h.date), 이름=h.name))
    src = spike_latest if as_of >= spike_hist["date"].max() else spike_hist[spike_hist["date"] == as_of]
    normal = kamis[kamis["date"] <= as_of].sort_values("date").groupby("item").last()
    for item in USAGE_ITEMS[s.group]:
        r = src[src["item"] == item].iloc[0]
        vs_normal = r["price"] / normal.loc[item, "normal_price"] - 1
        sig["ingredients"].append(dict(id=f"price_{item}", 품목=item, 현재가_원=int(r["price"]), 다음주_급등확률_퍼센트=round(r["prob"] * 100),
                                       급등경고=bool(r["prob"] >= ADVICE_ALERT_PROB), 평년대비_퍼센트=round(vs_normal * 100)))
    near = lic[(lic["group"] == s.group) & lic["open"].between(as_of - pd.Timedelta(days=90), as_of) & (lic["관리번호"] != s.관리번호)]
    for c in near.itertuples():
        dist = int(np.hypot(c.x - xy[0], c.y - xy[1]))
        if dist <= 500:
            sig["competitors_90d"].append(dict(id=f"comp_{c.open.date()}", 개업일=md(c.open), 업종=c.업태구분명, 거리_m=dist,
                                               경과일=int((as_of - c.open).days)))
    yr = str(as_of.year - 1)
    cy = closure[closure["year"] == yr]
    dong = cy[cy["adm_nm"] == s.adm_nm]
    if len(dong):
        sig["dong_closure"] = dict(id="closure", 연도=yr, 우리동네_음식점_폐업률_퍼센트=round(dong["rate"].iloc[0] * 100, 1),
                                   대구평균_폐업률_퍼센트=round(cy["closed"].sum() / cy["stock"].sum() * 100, 1))
    return sig


def actual_after(s, as_of):
    """과거 재현일 때만: 조언 뒤 7일 동안 실제로 일어난 일 (LLM에는 주지 않음)."""
    d = sales[sales["store_id"] == s.store_id].groupby("date")["total"].sum()
    nxt, prev = d[as_of + pd.Timedelta(days=1): as_of + pd.Timedelta(days=7)], d[as_of - pd.Timedelta(days=27): as_of]
    price = kamis.pivot(index="date", columns="item", values="price").ffill()
    chg = {it: round((price.loc[as_of + pd.Timedelta(days=1): as_of + pd.Timedelta(days=7), it].mean() / price.loc[as_of - pd.Timedelta(days=6): as_of, it].mean() - 1) * 100)
           for it in USAGE_ITEMS[s.group]}
    return dict(다음7일_일평균매출_변화_퍼센트=round((nxt.mean() / prev.mean() - 1) * 100), 다음7일_식자재_주평균가격_변화_퍼센트=chg)


class Advice(BaseModel):
    title: str = Field(description="한 줄 제목")
    reason: str = Field(description="근거. signals에 있는 숫자만 인용")
    action: str = Field(description="사장님이 이번 주에 할 구체적인 행동")
    urgency: Literal["높음", "보통", "참고"]
    evidence: list[str] = Field(description="근거로 쓴 signals 항목 id (festival_..., price_..., comp_..., closure, weather, sales_recent, holiday)")


class AdviceSet(BaseModel):
    summary: str = Field(description="이번 주 한 문장 요약")
    advices: list[Advice] = Field(description="중요한 순서로 1~3개")


SYSTEM = """당신은 대구 음식점 사장님을 돕는 '동네 주치의'입니다. 입력으로 주는 '가게 신호 요약'만 보고 이번 주 조언을 씁니다.
규칙:
- 숫자와 날짜는 signals에 있는 값만 그대로 쓰세요(예: 추석 전날처럼 날짜를 계산해 새로 만들지 말 것). 더하거나 곱해서 새 숫자(예: 예상 매출액, 필요한 인원 수, 발주량)를 만들지 마세요.
- signals에 없는 사실(다른 축제, 날씨, 가게 사정)을 지어내지 마세요. 예보가 없는 날의 날씨는 말하지 마세요.
- 가게 이름이나 주소는 쓰지 말고, 업종과 동네까지만 쓰세요.
- 급등경고가 있는 식자재, 7일 안 1km 이내 축제, 90일 안 근처 경쟁점, 14일 안 설날·추석, 강수 5mm 이상이거나 강수확률 60% 이상인 날이 있으면 반드시 조언에 반영하세요.
- 날짜는 signals에 적힌 표기 그대로(예: 9월 19일(토)) 쓰세요. '○일까지', '○일부터'처럼 signals에 없는 날짜를 만들지 마세요.
- 인원 수(○명), 며칠분, 수량(○kg, ○개)은 쓰지 마세요. 준비할 것은 수량 없이 말하세요.
- reason과 action은 사장님이 읽는 자연스러운 문장으로 쓰세요. signals의 키 이름(예: 거리_m, 배달비중_퍼센트)이나 id(예: festival_123)를 문장에 쓰지 말고, id는 evidence 배열에만 넣으세요.
- 조언은 최대 3개, 중요한 순서로. 존댓말로 짧고 쉽게. 행동은 '무엇을 언제 할지'가 드러나게."""


NUM_UNIT = re.compile(r"(\d[\d,]*(?:\.\d+)?)\s*(%|퍼센트|원|mm|°C|도|m|km|곳|건|일)")
DATE = re.compile(r"(\d{1,2})월\s*(\d{1,2})일")
QTY = re.compile(r"\d+\s*(?:명|인분|일분|kg|개|박스)")  # 인원·수량은 signals에 없으므로 나오면 무조건 지어낸 값
RAW_KEY = re.compile(r"[가-힣A-Za-z]+_[가-힣A-Za-z0-9]+")  # JSON 키·id가 문장에 그대로 노출


def allowed_numbers(sig):
    text = json.dumps(sig, ensure_ascii=False)
    nums = {float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", text)}
    return nums | {abs(n) for n in nums}


def validate(sig, adv):
    text = " ".join([adv.summary] + [f"{a.title} {a.reason} {a.action}" for a in adv.advices])
    sig_dates = set(DATE.findall(json.dumps(sig, ensure_ascii=False)))
    bad_dates = [f"{m}월 {d}일" for m, d in DATE.findall(text) if (m, d) not in sig_dates]
    allowed = allowed_numbers(sig)
    stripped = DATE.sub(" ", text)
    bad_nums = []
    for raw, unit in NUM_UNIT.findall(stripped):
        v = float(raw.replace(",", ""))
        ok = any(abs(v - a) < 0.51 for a in allowed) or (unit == "원" and any(abs(v - a) / max(a, 1) < 0.01 for a in allowed))
        if not ok:
            bad_nums.append(f"{raw}{unit}")
    bad_nums += [q for q in QTY.findall(stripped) if q not in bad_nums]
    missing = []
    for f in sig["festivals_next7"]:  # 필수는 매출 영향이 큰 1km 안 축제만 (가상 매출 규칙: 1km +25%, 2km +10%)
        if f["거리_m"] <= 1000 and f["이름"].split()[0][:4] not in text:
            missing.append(f["이름"])
    for i in sig["ingredients"]:
        if i["급등경고"] and i["품목"] not in text:
            missing.append(f"{i['품목']} 급등경고")
    if sig["competitors_90d"] and not re.search("경쟁|개업|새로 생", text):
        missing.append("근처 경쟁점 개업")
    for h in sig["holidays_next14"]:
        if re.search("설날|추석", h["이름"]) and h["이름"][:2] not in text:
            missing.append(h["이름"])
    wet = [d for d in sig["weather_next7"]["days"] if d.get("강수량_mm", 0) >= 5 or d.get("강수확률_최대", 0) >= 60]
    # "준비"의 '비'가 통과되지 않도록 비 관련 표현만 인정
    if wet and not re.search(r"(?<![가-힣])비\s*(?:예보|소식|오|가|를|와|로|에|날)|우천|강수|빗", text):
        missing.append("비 예보")
    raw_keys = sorted(set(RAW_KEY.findall(text)))
    return dict(invented_numbers=bad_nums, invented_dates=bad_dates, missing_signals=missing, format_issues=raw_keys,
                passed=not (bad_nums or bad_dates or missing or raw_keys))


SCHEMA_NOTE = ("\n\n출력은 설명 없이 아래 JSON 스키마를 따르는 JSON 객체 하나만 쓰세요.\n"
               + json.dumps(AdviceSet.model_json_schema(), ensure_ascii=False))


def call_llm(contents):
    """모델마다 구조화 출력 지원이 달라서, 스키마를 프롬프트로 주고 Pydantic으로 검증. 형식이 틀리면 None."""
    resp = client.chat.completions.create(
        model=MODEL, temperature=0.2, max_tokens=2048,
        messages=[{"role": "system", "content": SYSTEM + SCHEMA_NOTE}, {"role": "user", "content": contents}],
        # 추론 모드 끔(짧은 조언엔 불필요, 느림). 키 이름이 모델마다 달라 둘 다 전달: DeepSeek=thinking, Nemotron=enable_thinking
        extra_body={"chat_template_kwargs": {"thinking": False, "enable_thinking": False}},
    )
    text = resp.choices[0].message.content or ""
    body = text[text.find("{"): text.rfind("}") + 1]  # ```json 감싸기 등 제거
    try:
        return AdviceSet.model_validate_json(body), MODEL
    except ValidationError as e:
        print(f"  JSON 형식 오류: {e.errors()[0]['msg']}")
        return None, MODEL


def brief(sig):
    """LLM 입력용 문장형 요약. JSON 키를 그대로 주면 모델이 '거리_m' 같은 키 이름을 문장에 옮겨 적어서 사람이 읽는 표현으로 변환.
    화면용 계약(signals JSON)은 그대로 두고 LLM 입력만 바꿈. 검증은 원래 signals 기준."""
    st, sr, w = sig["store"], sig["sales_recent"], sig["weather_next7"]
    must, lines, ids = [], [], ["sales_recent", "weather_next7"]
    for f in sig["festivals_next7"]:
        (must if f["거리_m"] <= 1000 else lines).append(f"근처 축제: {f['이름']}, {f['기간']}, 가게에서 {f['거리_m']}m")
        ids.append(f["id"])
    for c in sig["competitors_90d"]:
        must.append(f"근처 경쟁점 개업: {c['개업일']} 같은 업종({c['업종']}) 가게가 {c['거리_m']}m 거리에 새로 생김({c['경과일']}일 전)")
        ids.append(c["id"])
    for h in sig["holidays_next14"]:
        (must if re.search("설날|추석", h["이름"]) else lines).append(f"공휴일: {h['날짜']} {h['이름']}")
    for d in w["days"]:
        wet = d.get("강수량_mm", 0) >= 5 or d.get("강수확률_최대", 0) >= 60
        txt = f"{d['date']} 최고 {d['최고기온']}도 최저 {d['최저기온']}도, 강수량 {d['강수량_mm']}mm" + (f", 강수확률 {d['강수확률_최대']}%" if "강수확률_최대" in d else "")
        (must if wet else lines).append(("비 예보: " if wet else "날씨: ") + txt)
    for i in sig["ingredients"]:
        txt = f"{i['품목']} 현재 {i['현재가_원']}원, 다음 주 급등 확률 {i['다음주_급등확률_퍼센트']}%, 평년보다 {abs(i['평년대비_퍼센트'])}% {'비쌈' if i['평년대비_퍼센트'] > 0 else '쌈'}"
        (must if i["급등경고"] else lines).append(("급등 경고 식자재: " if i["급등경고"] else "식자재: ") + txt)
        ids.append(i["id"])
    if "dong_closure" in sig:
        dc = sig["dong_closure"]
        lines.append(f"동네 음식점 폐업률({dc['연도']}년): 우리 동네 {dc['우리동네_음식점_폐업률_퍼센트']}%, 대구 평균 {dc['대구평균_폐업률_퍼센트']}%")
        ids.append("closure")
    out = [f"가게: {st['동네']} {st['업종']} / 기준일 {sig['기준일']}",
           f"매출: 최근 28일 하루 평균 {sr['최근28일_일평균매출_원']}원, 배달 비중 {sr['배달비중_퍼센트']}%, "
           f"최근 1년 비 오는 날 배달 매출 {sr['최근1년_비오는날_배달매출_변화_퍼센트']}% 변화, 금·토 매출 {sr['최근1년_금토_매출_변화_퍼센트']}% 변화",
           f"날씨 출처: {w['source']}"]
    out += ["", "[반드시 조언에 반영할 신호]"] + (must or ["없음"])
    out += ["", "[참고 신호]"] + (lines or ["없음"])
    out += ["", "evidence에 쓸 수 있는 id: " + ", ".join(ids + ["holiday"])]
    return "\n".join(out)


def generate(sig, max_attempts=3):
    attempts, feedback = [], ""
    for n in range(1, max_attempts + 1):
        adv, model = call_llm(brief(sig) + feedback)
        if adv is None:
            attempts.append(dict(attempt=n, model=model, invented_numbers=[], invented_dates=[], missing_signals=["JSON 형식 오류"], format_issues=[], passed=False))
            feedback = "\n\n[이전 답변이 JSON 스키마에 맞지 않음 - JSON 객체만 다시 출력]"
            continue
        check = validate(sig, adv)
        attempts.append(dict(attempt=n, model=model, **check))
        if check["passed"]:
            return adv, attempts
        last_ok = adv
        feedback = ("\n\n[이전 답변 검증 실패 - 고쳐서 다시 작성] 지어낸 숫자: " + ", ".join(check["invented_numbers"] + check["invented_dates"])
                    + " / 빠진 신호: " + ", ".join(check["missing_signals"])
                    + " / 문장에 노출된 키: " + ", ".join(check["format_issues"]))
    if "last_ok" not in locals():
        raise RuntimeError(f"{max_attempts}번 모두 JSON 형식 오류 - 다른 모델(NVIDIA_MODEL)을 시도하세요")
    return last_ok, attempts  # 검증 실패 결과도 저장하되 validation에 실패로 기록됨


# 검사기 자체 시험: 일부러 틀린 조언을 넣으면 반드시 실패해야 함 (API 호출 없음)
_sig = signals_for(next(stores.itertuples()), pd.Timestamp("2026-06-29"))  # S1: 치맥페스티벌·7월 1일 비 있음
_bad = AdviceSet(summary="이번 주 매출이 35% 오를 거예요", advices=[Advice(
    title="7월 9일 할인 행사", reason="평소보다 12,000원 더 벌 수 있어요 (거리_m 879)", action="직원을 2명 늘리고 닭 3일분 준비", urgency="높음", evidence=[])])
_chk = validate(_sig, _bad)
assert not _chk["passed"] and "35%" in _chk["invented_numbers"] and "12,000원" in _chk["invented_numbers"], _chk
assert "2명" in _chk["invented_numbers"] and "거리_m" in _chk["format_issues"], _chk
assert "7월 9일" in _chk["invented_dates"] and "대구치맥페스티벌" in _chk["missing_signals"] and "비 예보" in _chk["missing_signals"], _chk
print("검사기 자체 시험 통과:", _chk)

def run_job(job):
    as_of, s, sig = job
    path = OUT / as_of.strftime("%Y-%m-%d") / f"{s.store_id}.json"
    # 같은 모델로 만든 조언이 '현재' 검사 기준도 통과하면 건너뜀 (검사 기준이 강화되면 기존 조언도 다시 검사)
    if path.exists() and (_r := json.loads(path.read_text(encoding="utf-8"))).get("model") == MODEL and validate(sig, AdviceSet(**_r["advice"]))["passed"]:
        r = json.loads(path.read_text(encoding="utf-8"))
        a = r["validation"]
        return dict(as_of=r["as_of"], store_id=s.store_id, model=MODEL, attempts=len(a), passed=a[-1]["passed"], first_try_passed=a[0]["passed"],
                    invented=len(a[-1]["invented_numbers"]) + len(a[-1]["invented_dates"]), missing=len(a[-1]["missing_signals"]))
    try:
        adv, attempts = generate(sig)
    except (openai.APIError, RuntimeError) as e:  # 한 가게 실패로 전체를 멈추지 않음 → 다시 실행하면 이어서 생성
        print(f"[{as_of.date()} {s.store_id}] 실패: {str(e)[:120]}", flush=True)
        return dict(as_of=str(as_of.date()), store_id=s.store_id, model=MODEL, attempts=0, passed=False, first_try_passed=False, invented=None, missing=None)
    rec = dict(as_of=str(as_of.date()), mode="live" if as_of == LIVE_DATE else "replay", model=attempts[-1]["model"],
               signals=sig, advice=adv.model_dump(), validation=attempts)
    if as_of < LIVE_DATE:
        rec["actual_after"] = actual_after(s, as_of)
    folder = OUT / as_of.strftime("%Y-%m-%d")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{s.store_id}.json").write_text(json.dumps(rec, ensure_ascii=False, indent=2), encoding="utf-8")
    last = attempts[-1]
    print(f"[{as_of.date()} {s.store_id}] 시도 {len(attempts)}회, 통과={last['passed']} | {adv.summary}", flush=True)
    return dict(as_of=str(as_of.date()), store_id=s.store_id, model=last["model"], attempts=len(attempts), passed=last["passed"],
                first_try_passed=attempts[0]["passed"], invented=len(last["invented_numbers"]) + len(last["invented_dates"]),
                missing=len(last["missing_signals"]))


from concurrent.futures import ThreadPoolExecutor  # noqa: E402

# 신호 계산은 먼저(메인 스레드), LLM 호출만 동시에 (무료 등급 분당 약 40회 한도 안)
jobs = [(pd.Timestamp(a), s, signals_for(s, pd.Timestamp(a))) for a in sys.argv[1:] for s in stores.itertuples()]
with ThreadPoolExecutor(max_workers=2) as ex:
    rows = list(ex.map(run_job, jobs))

summary = pd.DataFrame(rows)
summary.to_csv(OUT / "validation_summary.csv", index=False, encoding="utf-8-sig")
print("\n", summary.to_string(index=False))
