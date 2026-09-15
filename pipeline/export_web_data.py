"""파이프라인 결과 → 웹 화면용 JSON (contracts/sample/) 내보내기. API 호출·키 없음.

usage: py -X utf8 pipeline/export_web_data.py
형식 설명은 contracts/README.md. 실존 가게 식별 정보(관리번호·정확 좌표·상호)는 내보내지 않음.
"""
import json
from pathlib import Path

import numpy as np
import pandas as pd

OUT = Path("contracts/sample")
ADVICE_SRC = Path("data/processed/advice")
LIVE_DATE = "2026-09-14"
DAYS = 90
ALERT_PROB = 0.30  # pipeline/make_advice.py ADVICE_ALERT_PROB 와 같게
UNITS = {"배추": "1포기", "무": "1개", "양파": "1kg", "대파": "1kg", "깐마늘": "1kg", "삼겹살": "100g", "닭": "1kg", "계란": "특란 30구"}


def dump(obj, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")  # OS 상관없이 LF


def nan_to_none(v):
    return None if isinstance(v, float) and np.isnan(v) else v


for old in OUT.rglob("*.json"):  # 폴더는 두고 파일만 지움 (Windows에서 탐색기가 폴더를 잡고 있으면 rmtree 실패)
    old.unlink()

# 가게 목록 + 동네 중심점(지도용, 가게 정확 위치 대신)
stores = pd.read_csv("data/processed/demo_stores.csv")
geo = json.load(open("data/raw/boundary/daegu_dong_ver20250401.geojson", encoding="utf-8"))["features"]
centroid = {}
for f in geo:
    g = f["geometry"]["coordinates"]
    ring = np.array(g[0][0] if f["geometry"]["type"] == "MultiPolygon" else g[0])
    lon, lat = ring.mean(axis=0)
    centroid[f["properties"]["adm_nm"].replace("대구광역시 ", "")] = (round(float(lat), 5), round(float(lon), 5))
dump([dict(id=s.store_id, login_id=f"demo-{s.store_id.lower()}", category=s.group, license_category=s.업태구분명,
           gu=s.gu, dong=s.adm_nm, role=s.role, scenario=s.scenario,
           dong_center=dict(lat=centroid[s.adm_nm][0], lon=centroid[s.adm_nm][1]))
      for s in stores.itertuples()], OUT / "stores.json")

# 조언: 파이프라인 결과 그대로
dates = sorted(p.name for p in ADVICE_SRC.iterdir() if p.is_dir())
for d in dates:
    for p in sorted((ADVICE_SRC / d).glob("S*.json")):
        dump(json.loads(p.read_text(encoding="utf-8")), OUT / "advice" / d / p.name)

# 매출: 가게별 최근 90일 일별
sales = pd.read_csv("data/processed/virtual_sales.csv", parse_dates=["date"])
last = sales["date"].max()
daily = (sales[sales["date"] > last - pd.Timedelta(days=DAYS)]
         .groupby(["store_id", "date"])
         .agg(hall=("sales_hall", "sum"), delivery=("sales_delivery", "sum"), orders=("orders", "sum"),
              ingredient_cost=("ingredient_cost", "sum"), is_rain=("is_rain", "first"), holiday=("holiday", "first"),
              fest_mult=("fest_mult", "first"), n_comp_90d=("n_comp_90d", "first"))
         .reset_index())
for sid, g in daily.groupby("store_id"):
    dump(dict(store_id=sid, is_virtual=True, unit="원",
              days=[dict(date=r.date.strftime("%Y-%m-%d"), hall=int(r.hall), delivery=int(r.delivery), total=int(r.hall + r.delivery),
                         orders=int(r.orders), ingredient_cost=int(r.ingredient_cost), is_rain=bool(r.is_rain),
                         holiday=r.holiday if isinstance(r.holiday, str) else None, fest_mult=float(r.fest_mult), n_comp_90d=int(r.n_comp_90d))
                    for r in g.itertuples()]),
         OUT / "sales" / f"{sid}.json")

# 식자재: 최근 90일 가격 + 최신 급등확률
kamis = pd.read_csv("data/raw/kamis/daegu_retail_daily.csv", parse_dates=["date"])
latest = pd.read_csv("data/processed/price_spike_latest.csv").set_index("item")
recent = kamis[kamis["date"] > kamis["date"].max() - pd.Timedelta(days=DAYS)]
dump(dict(as_of=kamis["date"].max().strftime("%Y-%m-%d"), region="대구", source="KAMIS 소매가격", alert_prob_threshold=ALERT_PROB,
          items=[dict(item=it, unit=UNITS[it],
                      latest=dict(price=int(latest.loc[it, "price"]), spike_prob=round(float(latest.loc[it, "prob"]), 3),
                                  spike_alert=bool(latest.loc[it, "prob"] >= ALERT_PROB),
                                  vs_normal=round(float(latest.loc[it, "vs_normal"]), 3), change_7d=round(float(latest.loc[it, "ret_7"]), 3)),
                      series=[dict(date=r.date.strftime("%Y-%m-%d"), price=int(r.price), normal_price=nan_to_none(float(r.normal_price)))
                              for r in g.sort_values("date").itertuples()])
                 for it, g in recent.groupby("item")]),
     OUT / "prices.json")

# 신뢰도: 모델·LLM 검증 수치 + 그래프 경로
sm = pd.read_csv("data/processed/sales_model_validation.csv")
ps = pd.read_csv("data/processed/price_spike_validation.csv")
adv = pd.read_csv(ADVICE_SRC / "validation_summary.csv")
rec = lambda df, cols: [{c: nan_to_none(v) for c, v in zip(cols, row)} for row in df[cols].itertuples(index=False)]
dump(dict(
    sales_model=dict(note="가상 매출 기준(성능 상한). 실데이터로 같은 파이프라인 재학습 예정",
                     learning_curve=rec(sm[sm["kind"] == "learning_curve"], ["train_months", "model", "baseline", "floor"]),
                     per_store=rec(sm[sm["kind"] == "per_store"], ["store_id", "model", "baseline", "floor"]),
                     effects=rec(sm[sm["kind"] == "effects"], ["factor", "truth", "m3", "m36"])),
    price_spike_model=dict(note="KAMIS 대구 실제 가격, 2026년(학습 미사용 기간) 검증",
                           overall=rec(ps[ps["kind"] == "overall"], ["who", "precision", "recall", "f1", "pr_auc", "base_rate"]),
                           per_item=rec(ps[ps["kind"] == "per_item"], ["item", "episodes", "caught_model", "caught_momentum"]),
                           learning_curve=rec(ps[ps["kind"] == "learning_curve"], ["train_months", "pr_auc"])),
    advice_llm=rec(adv, ["as_of", "store_id", "model", "attempts", "passed", "first_try_passed"]),
    figures=sorted(f"docs/figures/{p.name}" for p in Path("docs/figures").glob("*.png")),
), OUT / "validation.json")

dump(dict(advice_dates=dates, live_date=LIVE_DATE, replay_dates=[d for d in dates if d != LIVE_DATE],
          store_ids=stores["store_id"].tolist(), sales_last_date=last.strftime("%Y-%m-%d")), OUT / "index.json")

files = sorted(p.relative_to(OUT).as_posix() for p in OUT.rglob("*.json"))
assert len(files) == 4 + len(stores) + len(dates) * len(stores), files
text = "".join(p.read_text(encoding="utf-8") for p in OUT.rglob("*.json"))
assert "관리번호" not in text and '"x"' not in text, "실존 가게 식별 정보가 섞임"
print(f"{len(files)}개 파일 → {OUT}")
