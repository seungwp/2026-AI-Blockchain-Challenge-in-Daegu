"""식자재 가격 급등 예측 모델 + 검증 + 그래프 (KAMIS 대구 실제 소매가).

usage: py -X utf8 price_spike_model.py data/raw/kamis/daegu_retail_daily.csv data/raw/weather/daegu_asos143_daily_20230101_20260913.csv data/processed docs/figures

급등 = 다음 7일 평균가 ≥ 지난 7일 평균가 × 1.10  (하루짜리 튀는 값 대신 주 평균으로 판단)
입력 = 그날까지의 가격 흐름·평년 대비·계절·최근 대구 날씨 (미래 정보 없음)
검증 = 2026년(학습에 안 쓴 기간) 실제 가격. 기준선 = 모멘텀 규칙(최근 1주 평균이 전주보다 5%↑이면 경고)
"""
import sys
from pathlib import Path

import lightgbm as lgb
import matplotlib
import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, f1_score, precision_score, recall_score

matplotlib.use("Agg")
import matplotlib.pyplot as plt

kamis_csv, weather_csv, data, figs = sys.argv[1], sys.argv[2], Path(sys.argv[3]), Path(sys.argv[4])
figs.mkdir(parents=True, exist_ok=True)
H, TH, MOMENTUM = 7, 0.10, 0.05
LAST = pd.Timestamp("2026-09-13")
VAL_START, VAL_END = pd.Timestamp("2025-07-01"), pd.Timestamp("2025-12-23")
TEST_START, TEST_END = pd.Timestamp("2026-01-01"), LAST - pd.Timedelta(days=H)
PURGE = pd.Timedelta(days=H + 1)  # 학습 라벨이 검증 기간 가격을 보지 않도록

# ---------------- 데이터 ----------------
k = pd.read_csv(kamis_csv, parse_dates=["date"])
days = pd.date_range(k["date"].min(), LAST)
price = k.pivot(index="date", columns="item", values="price").reindex(days).ffill()   # 채소는 평일만 조사
normal = k.pivot(index="date", columns="item", values="normal_price").reindex(days).ffill()
w = pd.read_csv(weather_csv, encoding="cp949", parse_dates=["일시"]).set_index("일시").reindex(days)
rain = w["일강수량(mm)"].fillna(0)
tmax, tmin = w["최고기온(°C)"].ffill(), w["최저기온(°C)"]

frames = []
for item in price.columns:
    p = price[item]
    past7 = p.rolling(7).mean()
    next7 = p[::-1].rolling(7).mean()[::-1].shift(-1)          # t+1 ~ t+7 평균
    daily_ret = p.pct_change()
    df = pd.DataFrame({
        "item": item, "date": days, "price": p.values,
        "ret_1": daily_ret.values,
        "ret_7": (past7 / past7.shift(7) - 1).values,
        "ret_14": (past7 / past7.shift(14) - 1).values,
        "ret_28": (past7 / past7.shift(28) - 1).values,
        "vs_normal": (p / normal[item] - 1).values,
        "pos_60": ((p - p.rolling(60).min()) / (p.rolling(60).max() - p.rolling(60).min())).values,
        "vol_14": daily_ret.rolling(14).std().values,
        "month": days.month, "doy_sin": np.sin(2 * np.pi * days.dayofyear / 365.25), "doy_cos": np.cos(2 * np.pi * days.dayofyear / 365.25),
        "rain_14": rain.rolling(14).sum().values, "rain_30": rain.rolling(30).sum().values,
        "tmax_14": tmax.rolling(14).mean().values, "tmin_14": tmin.rolling(14).mean().values,
        "heat_14": (tmax >= 33).rolling(14).sum().values,
        "momentum_alert": (past7 / past7.shift(7) - 1 >= MOMENTUM).values,
        "future_ret": (next7 / past7 - 1).values,
    })
    frames.append(df)
d = pd.concat(frames, ignore_index=True)
d["item"] = d["item"].astype("category")
d["y"] = (d["future_ret"] >= TH).astype(int)
d = d[d["ret_28"].notna() & d["pos_60"].notna()]
FEATS = ["item", "ret_1", "ret_7", "ret_14", "ret_28", "vs_normal", "pos_60", "vol_14", "month", "doy_sin", "doy_cos",
         "rain_14", "rain_30", "tmax_14", "tmin_14", "heat_14"]
PARAMS = dict(n_estimators=400, learning_rate=0.03, num_leaves=15, min_child_samples=30, subsample=0.8, subsample_freq=1,
              colsample_bytree=0.8, reg_lambda=1.0, random_state=42, verbose=-1)


def fit(train):
    return lgb.LGBMClassifier(**PARAMS).fit(train[FEATS], train["y"])


def split(start, end):
    return d[(d["date"] >= start) & (d["date"] <= end) & d["future_ret"].notna()]


# 1) 경고 임계값은 2025 하반기(검증용)에서 정하고, 2026 시험에는 손대지 않음
val_model = fit(d[d["date"] <= VAL_START - PURGE])
val = split(VAL_START, VAL_END)
val_p = val_model.predict_proba(val[FEATS])[:, 1]
cands = np.arange(0.02, 0.80, 0.02)
thr = float(cands[np.argmax([f1_score(val["y"], val_p >= c) for c in cands])])

# 2) 최종 모델: 2025-12-23까지 학습 → 2026 시험
model = fit(d[d["date"] <= TEST_START - PURGE])
test = split(TEST_START, TEST_END).copy()
test["prob"] = model.predict_proba(test[FEATS])[:, 1]
test["alert"] = test["prob"] >= thr


def scores(y, alert, prob=None):
    r = dict(precision=precision_score(y, alert, zero_division=0), recall=recall_score(y, alert), f1=f1_score(y, alert))
    if prob is not None:
        r["pr_auc"] = average_precision_score(y, prob)
    return r


def episodes(g, col):
    """연속된 급등 라벨 구간 = 급등 1건. 구간 안에서 한 번이라도 경고하면 '사전 경고 성공'."""
    g = g.sort_values("date")
    ep_id = (g["y"].diff() != 0).cumsum()
    ep = g[g["y"] == 1].groupby(ep_id[g["y"] == 1])[col].any()
    return len(ep), int(ep.sum())


m_score = scores(test["y"], test["alert"], test["prob"])
b_score = scores(test["y"], test["momentum_alert"])
base_rate = test["y"].mean()
print(f"경고 임계값(2025H2에서 결정) = {thr:.2f} | 2026 급등 비율 {base_rate:.1%}")
print("모델   :", {k: round(v, 3) for k, v in m_score.items()})
print("모멘텀 :", {k: round(v, 3) for k, v in b_score.items()})

per_item = []
for item, g in test.groupby("item", observed=True):
    n, caught_m = episodes(g, "alert")
    _, caught_b = episodes(g, "momentum_alert")
    false_m = int((g["alert"] & (g["y"] == 0)).sum())
    false_b = int((g["momentum_alert"] & (g["y"] == 0)).sum())
    per_item.append(dict(item=item, episodes=n, caught_model=caught_m, caught_momentum=caught_b,
                         false_alert_days_model=false_m, false_alert_days_momentum=false_b))
per_item = pd.DataFrame(per_item)
print("\n품목별 2026 급등 건수와 사전 경고:\n", per_item.to_string(index=False))

# 3) 학습 기간별 (임계값 없는 PR-AUC로 비교)
curve = []
for months in [6, 12, 24, 33]:
    start = TEST_START - PURGE - pd.DateOffset(months=months)
    mm = fit(d[(d["date"] >= start) & (d["date"] <= TEST_START - PURGE)])
    curve.append(dict(train_months=months, pr_auc=average_precision_score(test["y"], mm.predict_proba(test[FEATS])[:, 1])))
curve = pd.DataFrame(curve)
print("\n학습 기간별 PR-AUC:\n", curve.round(3).to_string(index=False), f"\n(무작위 경고 수준 = 급등 비율 {base_rate:.3f})")

imp = pd.Series(model.booster_.feature_importance("gain"), index=FEATS).sort_values(ascending=False)
print("\n중요 입력 상위 6:", (imp / imp.sum()).head(6).round(3).to_dict())

# ---------------- 검증 기준 (실패하면 멈추고 원인부터 확인) ----------------
assert m_score["f1"] > b_score["f1"], "모델 F1이 모멘텀 규칙보다 낮음"
assert m_score["pr_auc"] > 2 * base_rate, "PR-AUC가 무작위 경고의 2배도 안 됨"
assert per_item["caught_model"].sum() >= per_item["caught_momentum"].sum(), "사전 경고 건수가 규칙보다 적음"
print("\n검증 통과")

summary = pd.DataFrame([dict(kind="overall", who="model", threshold=thr, base_rate=base_rate, **m_score),
                        dict(kind="overall", who="momentum", base_rate=base_rate, **b_score)])
pd.concat([summary, per_item.assign(kind="per_item"), curve.assign(kind="learning_curve")]).to_csv(
    data / "price_spike_validation.csv", index=False, encoding="utf-8-sig")
test[["item", "date", "price", "prob", "alert", "momentum_alert", "y", "future_ret"]].to_csv(
    data / "price_spike_predictions_2026.csv", index=False, encoding="utf-8-sig")
# 조언용: 데이터 마지막 날 기준 다음 주 급등 확률 (정답은 아직 모름)
latest = d[d["date"] == LAST].copy()
latest["prob"] = model.predict_proba(latest[FEATS])[:, 1]
latest["alert"] = latest["prob"] >= thr
latest[["item", "date", "price", "prob", "alert", "ret_7", "vs_normal"]].to_csv(
    data / "price_spike_latest.csv", index=False, encoding="utf-8-sig")
print("\n최신일 급등 확률:", latest.set_index("item")["prob"].round(2).to_dict())

# ---------------- 그래프 ----------------
BLUE, ORANGE, INK, INK2, MUTED, GRID, AXIS, SURF = "#2a78d6", "#eb6834", "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#c3c2b7", "#fcfcfb"
plt.rcParams.update({"font.family": "Malgun Gothic", "axes.facecolor": SURF, "figure.facecolor": SURF, "axes.edgecolor": AXIS,
                     "axes.labelcolor": INK2, "xtick.color": MUTED, "ytick.color": MUTED, "axes.grid": True, "grid.color": GRID,
                     "grid.linewidth": 0.6, "axes.spines.top": False, "axes.spines.right": False, "axes.titlecolor": INK,
                     "axes.titlesize": 12, "axes.titleweight": "bold", "legend.frameon": False, "axes.unicode_minus": False})
pct = matplotlib.ticker.PercentFormatter(1.0, decimals=0)
NOTE = "KAMIS 대구 실제 소매가 · 학습 2023-03~2025-12 · 검증 2026-01~09 (학습에 안 쓴 기간) · 급등 = 다음 7일 평균이 지난 7일보다 10%↑"

# 1) 2026 타임라인: 가격, 실제 급등 구간, 모델 경고
top_items = per_item.sort_values("episodes", ascending=False)["item"].head(2).tolist()
fig, axes = plt.subplots(len(top_items), 1, figsize=(11, 6.4), sharex=True)
for ax, item in zip(axes, top_items):
    g = test[test["item"] == item].sort_values("date")
    ax.plot(g["date"], g["price"], color=INK2, lw=1.4, label="실제 가격")
    for dt in g.loc[g["y"] == 1, "date"]:
        ax.axvspan(dt, dt + pd.Timedelta(days=1), color=ORANGE, alpha=0.18, lw=0)
    lo = g["price"].min() * 0.9
    ax.scatter(g.loc[g["alert"], "date"], np.full(g["alert"].sum(), lo), color=BLUE, s=14, label="모델 경고", zorder=3)
    r = per_item.set_index("item").loc[item]
    ax.set_title(f"{item}   급등 {r.episodes}건 중 모델 {r.caught_model}건 · 규칙 {r.caught_momentum}건 사전 경고", loc="left")
    ax.set_ylabel("원")
    ax.set_ylim(lo * 0.95, None)
handles = [plt.Line2D([], [], color=INK2, lw=1.4), plt.Rectangle((0, 0), 1, 1, color=ORANGE, alpha=0.3),
           plt.Line2D([], [], color=BLUE, marker="o", lw=0, ms=5)]
axes[0].legend(handles, ["실제 가격", "급등 직전 구간(정답)", "모델 경고"], loc="upper left", bbox_to_anchor=(0, 1.22), ncol=3, fontsize=9)
fig.suptitle("2026년 실제 가격 급등을 미리 알렸나", x=0.01, ha="left", fontsize=14, fontweight="bold", color=INK)
fig.text(0.01, 0.005, NOTE, fontsize=8, color=MUTED)
fig.tight_layout(rect=(0, 0.02, 1, 0.95))
fig.savefig(figs / "price_spike_1_timeline.png", dpi=150)

# 2) 전체 성능: 모델 vs 규칙
fig, ax = plt.subplots(figsize=(8, 4.2))
names = [("recall", "급등 중 미리 경고한 비율"), ("precision", "경고가 맞은 비율"), ("f1", "종합 (F1)")]
xx = np.arange(len(names))
ax.bar(xx - 0.19, [b_score[k] for k, _ in names], width=0.36, color=ORANGE, label="규칙 (지난주보다 5%↑이면 경고)")
ax.bar(xx + 0.19, [m_score[k] for k, _ in names], width=0.36, color=BLUE, label="모델")
for i, (k, _) in enumerate(names):
    ax.text(i - 0.19, b_score[k] + 0.01, f"{b_score[k]:.0%}", ha="center", fontsize=9, color=INK2)
    ax.text(i + 0.19, m_score[k] + 0.01, f"{m_score[k]:.0%}", ha="center", fontsize=9, color=INK2)
ax.set_xticks(xx, [n for _, n in names])
ax.yaxis.set_major_formatter(pct)
ax.set_ylim(0, 1)
ax.set_title("2026년 급등 예측 성능", loc="left", pad=28)
ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2, fontsize=9)
ax.grid(axis="x", visible=False)
fig.text(0.01, 0.005, NOTE, fontsize=7, color=MUTED)
fig.tight_layout(rect=(0, 0.03, 1, 1))
fig.savefig(figs / "price_spike_2_overall.png", dpi=150)

# 3) 품목별 사전 경고 건수
pi = per_item[per_item["episodes"] > 0].sort_values("episodes")
fig, ax = plt.subplots(figsize=(8, 4.4))
yy = np.arange(len(pi))
ax.barh(yy, pi["episodes"], height=0.7, color=GRID, label="2026년 실제 급등 건수")
ax.barh(yy + 0.17, pi["caught_momentum"], height=0.3, color=ORANGE, label="규칙이 미리 경고")
ax.barh(yy - 0.17, pi["caught_model"], height=0.3, color=BLUE, label="모델이 미리 경고")
for i, r in enumerate(pi.itertuples()):
    ax.text(r.episodes + 0.15, i, f"{r.caught_model}/{r.episodes}", va="center", fontsize=9, color=INK2)
ax.set_yticks(yy, pi["item"])
ax.set_xlabel("건수")
ax.set_title("품목별: 급등 몇 건을 미리 잡았나", loc="left", pad=28)
ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=3, fontsize=9)
ax.grid(axis="y", visible=False)
fig.tight_layout()
fig.savefig(figs / "price_spike_3_per_item.png", dpi=150)

# 4) 학습 데이터 기간별
fig, ax = plt.subplots(figsize=(8, 4.4))
ax.plot(curve["train_months"], curve["pr_auc"], color=BLUE, lw=2, marker="o", ms=7, label="모델")
ax.axhline(base_rate, color=MUTED, lw=1, label="무작위로 경고했을 때")
for x, v in zip(curve["train_months"], curve["pr_auc"]):
    ax.annotate(f"{v:.2f}", (x, v), textcoords="offset points", xytext=(0, 8), ha="center", fontsize=9, color=INK2)
ax.set_xticks(curve["train_months"], [f"{m}개월" for m in curve["train_months"]])
ax.set_ylim(0, max(curve["pr_auc"].max() * 1.3, 0.3))
ax.set_xlabel("학습에 쓴 가격 데이터 기간")
ax.set_ylabel("PR-AUC (높을수록 정확)")
ax.set_title("가격 데이터가 쌓일수록 급등을 더 잘 잡는다", loc="left")
ax.legend(loc="lower right", fontsize=9)
fig.tight_layout()
fig.savefig(figs / "price_spike_4_learning_curve.png", dpi=150)
print("그래프 저장:", sorted(p.name for p in figs.glob("price_spike_*.png")))
