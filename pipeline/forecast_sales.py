"""다음 7일 매출 예측 (데모 가게 6곳) + 과거 재현 주간 검증 + 그래프.

usage: py -X utf8 pipeline/forecast_sales.py 2026-06-29 2026-09-14

- 기준일까지의 (가상) 매출만으로 학습 → 기준일 다음 7일 예측 (미래 매출은 학습에 쓰지 않음)
- 입력은 미리 알 수 있는 값만: 요일·휴일·축제 일정·경쟁점 개업·날씨
  · 실시간(2026-09-14): 기상청 단기예보. 예보가 없는 날은 날씨 미상(weather_known=false, 비·폭염 없음으로 가정)
  · 과거 재현: 실측 날씨를 예보 대신 사용 → 실제 매출과 비교해 검증
- 기준선: 최근 4주 같은 요일 평균 (사장님 감)
결과: data/processed/forecast/<기준일>/<가게ID>.json, docs/figures/forecast_1_replay.png
"""
import json
import sys
from pathlib import Path

import matplotlib
import numpy as np
import pandas as pd
from pyproj import Transformer

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from sales_features import TARGETS, fit, predict, wmape

OUT, FIG = Path("data/processed/forecast"), Path("docs/figures")
LIVE_DATE = pd.Timestamp("2026-09-14")
# make_virtual_sales.py 와 같은 기준 (가상 매출 규칙과 일치해야 입력 의미가 같음)
RAIN_MM, HEAT_C, COLD_C, FEST_1KM, FEST_2KM, COMP_DAYS = 5, 33, -5, 1.25, 1.10, 90
GROUP = {"통닭(치킨)": "치킨", "호프/통닭": "치킨", "한식": "한식", "식육(숯불구이)": "고깃집", "분식": "분식", "중국식": "중국식"}

stores = pd.read_csv("data/processed/demo_stores.csv")
sales = pd.read_csv("data/processed/virtual_sales.csv", parse_dates=["date"])
w = pd.read_csv("data/raw/weather/daegu_asos143_daily_20230101_20260913.csv", encoding="cp949", parse_dates=["일시"]).set_index("일시")
fc = pd.read_csv("data/raw/forecast/daegu_weather_forecast.csv", dtype=str)
hol = pd.read_csv("data/holidays_kr.csv", parse_dates=["date"]).set_index("date")["name"]
fests = pd.read_csv("data/raw/festival/daegu_festival_tourapi.csv", parse_dates=["start", "end"])
fests["x"], fests["y"] = Transformer.from_crs("EPSG:4326", "EPSG:5174", always_xy=True).transform(fests["lon"].values, fests["lat"].values)
lic = pd.read_csv("data/processed/stores_with_dong.csv", dtype=str)
lic["open"], lic["close"] = pd.to_datetime(lic["open"]), pd.to_datetime(lic["close"])
lic["x"], lic["y"] = pd.to_numeric(lic["좌표정보X(EPSG5174)"]), pd.to_numeric(lic["좌표정보Y(EPSG5174)"])
lic["group"] = lic["업태구분명"].map(GROUP)
lic = lic[~(lic["소재지전체주소"].str.contains("일원|축제", na=False) | ((lic["close"] - lic["open"]).dt.days < 30)) & lic["x"].notna()]


def weather_flags(as_of, days):
    """날짜별 (is_rain, is_heat, is_cold, weather_known)."""
    rows = {}
    for dt in days:
        if as_of == LIVE_DATE:
            f = fc[fc["fcstDate"] == dt.strftime("%Y%m%d")]
            if f.empty:
                rows[dt] = (False, False, False, False)
                continue
            num = lambda c: pd.to_numeric(f.loc[f["category"] == c, "fcstValue"].str.extract(r"(-?[\d.]+)")[0], errors="coerce")
            tmax = num("TMX").max() if num("TMX").notna().any() else num("TMP").max()
            tmin = num("TMN").min() if num("TMN").notna().any() else num("TMP").min()
            rows[dt] = (bool(num("PCP").fillna(0).sum() >= RAIN_MM or num("POP").max() >= 60), bool(tmax >= HEAT_C), bool(tmin <= COLD_C), True)
        else:
            r = w.loc[dt]
            rows[dt] = (bool(np.nan_to_num(r["일강수량(mm)"]) >= RAIN_MM), bool(r["최고기온(°C)"] >= HEAT_C), bool(r["최저기온(°C)"] <= COLD_C), True)
    return rows


def future_rows(s, as_of):
    days = pd.date_range(as_of + pd.Timedelta(days=1), periods=7)
    wf = weather_flags(as_of, days)
    dist = np.hypot(fests["x"] - s.x, fests["y"] - s.y)
    comp = lic[(lic["group"] == s.group) & (np.hypot(lic["x"] - s.x, lic["y"] - s.y) <= 500) & (lic["관리번호"] != s.관리번호)]
    rows = []
    for dt in days:
        on = (fests["start"] <= dt) & (fests["end"] >= dt)
        fest = FEST_1KM if (on & (dist <= 1000)).any() else FEST_2KM if (on & (dist <= 2000)).any() else 1.0
        n_comp = int(((comp["open"] <= dt) & (comp["open"] > dt - pd.Timedelta(days=COMP_DAYS))).sum())
        rain, heat, cold, known = wf[dt]
        for slot in ["점심", "저녁", "심야"]:
            rows.append(dict(store_id=s.store_id, date=dt, time_slot=slot, holiday=hol.get(dt), is_rain=rain, is_heat=heat,
                             is_cold=cold, fest_mult=fest, n_comp_90d=n_comp, weather_known=known))
    return pd.DataFrame(rows)


def baseline(daily, dt):
    past = [daily.get(dt - pd.Timedelta(days=7 * k)) for k in range(1, 5)]
    past = [v for v in past if v is not None]
    return float(np.mean(past))


results = {}
for a in sys.argv[1:]:
    as_of = pd.Timestamp(a)
    train = sales[sales["date"] <= as_of]
    models = {t: fit(train, t) for t in TARGETS}
    fut = pd.concat([future_rows(s, as_of) for s in stores.itertuples()], ignore_index=True)
    fut = fut.join(predict(models, fut))
    for s in stores.itertuples():
        f = fut[fut["store_id"] == s.store_id]
        hist = train[train["store_id"] == s.store_id].groupby("date")[TARGETS].sum().sum(axis=1)
        actual = sales[sales["store_id"] == s.store_id].groupby("date")[TARGETS].sum().sum(axis=1)
        days = []
        for dt, g in f.groupby("date"):
            r0 = g.iloc[0]
            day = dict(date=dt.strftime("%Y-%m-%d"),
                       predicted=dict(hall=int(round(g["sales_hall"].sum(), -2)), delivery=int(round(g["sales_delivery"].sum(), -2)),
                                      total=int(round(g[TARGETS].sum().sum(), -2))),
                       baseline_total=int(round(baseline(hist, dt), -2)), weather_known=bool(r0["weather_known"]),
                       drivers=dict(is_rain=bool(r0["is_rain"]), is_heat=bool(r0["is_heat"]), is_cold=bool(r0["is_cold"]),
                                    holiday=r0["holiday"] if isinstance(r0["holiday"], str) else None,
                                    fest_mult=float(r0["fest_mult"]), n_comp_90d=int(r0["n_comp_90d"])))
            if dt in actual.index and as_of < LIVE_DATE:
                day["actual_total"] = int(actual[dt])
            days.append(day)
        pred7 = sum(d["predicted"]["total"] for d in days)
        base7 = sum(d["baseline_total"] for d in days)
        summary = dict(next7_predicted_total=pred7, next7_baseline_total=base7, change_vs_baseline=round(pred7 / base7 - 1, 3),
                       weather_known_days=sum(d["weather_known"] for d in days))
        if all("actual_total" in d for d in days):
            y = np.array([d["actual_total"] for d in days])
            summary |= dict(next7_actual_total=int(y.sum()),
                            wmape_model=round(wmape(y, np.array([d["predicted"]["total"] for d in days])), 3),
                            wmape_baseline=round(wmape(y, np.array([d["baseline_total"] for d in days])), 3))
        rec = dict(store_id=s.store_id, as_of=a, mode="live" if as_of == LIVE_DATE else "replay", is_virtual=True,
                   model="LightGBM (기준일까지 가상 매출로 학습)", unit="원", summary=summary, days=days)
        (OUT / a).mkdir(parents=True, exist_ok=True)
        (OUT / a / f"{s.store_id}.json").write_text(json.dumps(rec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        results[(a, s.store_id)] = rec
    print(f"[{a}] " + ", ".join(f"{sid} {results[(a, sid)]['summary']['change_vs_baseline']:+.0%}" for sid in stores["store_id"]))

# ---------------- 검증 (과거 재현 기준일) ----------------
for a in [x for x in sys.argv[1:] if pd.Timestamp(x) < LIVE_DATE]:
    recs = [results[(a, sid)] for sid in stores["store_id"]]
    y = np.concatenate([[d["actual_total"] for d in r["days"]] for r in recs])
    pm = np.concatenate([[d["predicted"]["total"] for d in r["days"]] for r in recs])
    pb = np.concatenate([[d["baseline_total"] for d in r["days"]] for r in recs])
    mw, bw = wmape(y, pm), wmape(y, pb)
    print(f"\n[{a} 재현 검증] 다음 7일 가게·일 매출 오차: 모델 {mw:.1%} vs 사장님 감 {bw:.1%}")
    for r in recs:
        print(f"  {r['store_id']}: 모델 {r['summary']['wmape_model']:.1%}  감 {r['summary']['wmape_baseline']:.1%}")
    assert len(y) == 42 and (pm > 0).all(), "예측 행 수·값 이상"
    # 7일×6곳은 표본이 작아 가게별로는 뒤집힐 수 있음 → 전체 기준만 판정
    assert mw < bw, f"재현 주간 전체 오차가 기준선보다 큼 (모델 {mw:.3f} ≥ 감 {bw:.3f})"

    BLUE, ORANGE, INK, INK2, MUTED, GRID, AXIS, SURF = "#2a78d6", "#eb6834", "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#c3c2b7", "#fcfcfb"
    plt.rcParams.update({"font.family": "Malgun Gothic", "axes.facecolor": SURF, "figure.facecolor": SURF, "axes.edgecolor": AXIS,
                         "axes.labelcolor": INK2, "xtick.color": MUTED, "ytick.color": MUTED, "axes.grid": True, "grid.color": GRID,
                         "grid.linewidth": 0.6, "axes.spines.top": False, "axes.spines.right": False, "axes.titlecolor": INK,
                         "axes.titlesize": 10, "axes.titleweight": "bold", "legend.frameon": False, "axes.unicode_minus": False})
    fig, axes = plt.subplots(2, 3, figsize=(12, 6.4), sharex=True)
    for ax, r, s in zip(axes.flat, recs, stores.itertuples()):
        x = [d["date"][5:] for d in r["days"]]
        ax.plot(x, [d["actual_total"] / 1e4 for d in r["days"]], color=MUTED, lw=1.5, marker="o", ms=4, label="실제(가상)")
        ax.plot(x, [d["baseline_total"] / 1e4 for d in r["days"]], color=ORANGE, lw=2, label="사장님 감")
        ax.plot(x, [d["predicted"]["total"] / 1e4 for d in r["days"]], color=BLUE, lw=2, label="모델 예측")
        ax.set_title(f"{r['store_id']} {s.group} · 오차 {r['summary']['wmape_model']:.0%} / 감 {r['summary']['wmape_baseline']:.0%}", loc="left")
        ax.tick_params(axis="x", labelrotation=45, labelsize=8)
    for ax in axes[:, 0]:
        ax.set_ylabel("일 매출(만원)")
    axes[0, 0].legend(loc="upper left", fontsize=8)
    fig.suptitle(f"{a} 기준 다음 7일 매출 예측 vs 실제 · 전체 오차 모델 {mw:.1%} / 사장님 감 {bw:.1%}",
                 x=0.01, ha="left", fontsize=13, fontweight="bold", color=INK)
    fig.text(0.01, 0.005, "기준일까지의 매출로만 학습 · 날씨는 실측값을 예보 대신 사용(과거 재현) · 매출은 규칙 기반 가상 데이터",
             fontsize=8, color=MUTED)
    fig.tight_layout(rect=(0, 0.02, 1, 0.95))
    FIG.mkdir(parents=True, exist_ok=True)
    fig.savefig(FIG / "forecast_1_replay.png", dpi=150)
print("\n검증 통과, 저장:", OUT)
