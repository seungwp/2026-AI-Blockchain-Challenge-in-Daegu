"""매출 예측 모델 검증 + 그래프 (가상 매출 기준).

usage: py -X utf8 validate_sales_model.py data/processed docs/figures

- 학습 ~2025-12-31, 검증 2026-01-01 ~ 2026-09-13 (모델이 본 적 없는 기간)
- 입력은 전부 '미리 알 수 있는 값'(요일·휴일·날씨예보·축제일정·경쟁점 개업) → 실서비스에서 다음 주 예측 가능
- 기준선: 사장님 감 = 최근 4주 같은 요일 평균
- 한계선: 잡음을 뺀 기대값(virtual_sales_expected.csv)으로 예측했을 때의 오차 = 어떤 모델도 넘을 수 없는 선
- 가상 매출은 규칙으로 만들었으므로 여기 성능은 상한. 실데이터로 바꾸면 같은 코드로 다시 학습·검증
"""
import sys
from pathlib import Path

import matplotlib
import numpy as np
import pandas as pd

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sales_features import TARGETS, daily_total, features, fit, predict, wmape

data, figs = Path(sys.argv[1]), Path(sys.argv[2])
figs.mkdir(parents=True, exist_ok=True)
TEST_START, TRAIN_END = "2026-01-01", "2025-12-31"
MONTHS = [3, 6, 12, 24, 36]

BLUE, ORANGE, INK, INK2, MUTED, GRID, AXIS, SURF = "#2a78d6", "#eb6834", "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#c3c2b7", "#fcfcfb"
plt.rcParams.update({"font.family": "Malgun Gothic", "axes.facecolor": SURF, "figure.facecolor": SURF, "axes.edgecolor": AXIS,
                     "axes.labelcolor": INK2, "xtick.color": MUTED, "ytick.color": MUTED, "axes.grid": True, "grid.color": GRID,
                     "grid.linewidth": 0.6, "axes.spines.top": False, "axes.spines.right": False, "axes.titlecolor": INK,
                     "axes.titlesize": 12, "axes.titleweight": "bold", "legend.frameon": False, "axes.unicode_minus": False})

s = pd.read_csv(data / "virtual_sales.csv", parse_dates=["date"])
exp = pd.read_csv(data / "virtual_sales_expected.csv", parse_dates=["date"])
test = s[s["date"] >= TEST_START]
y_true = daily_total(test, TARGETS)

# 기준선: 최근 4주 같은 요일 평균 (가게×시간대×채널별)
wide = s.set_index(["store_id", "time_slot", "date"])[TARGETS].sort_index()
base_parts = []
for lag in (7, 14, 21, 28):
    shifted = s[["store_id", "time_slot", "date"] + TARGETS].copy()
    shifted["date"] = shifted["date"] + pd.Timedelta(days=lag)
    base_parts.append(shifted.set_index(["store_id", "time_slot", "date"]))
baseline = pd.concat(base_parts).groupby(level=[0, 1, 2]).mean()
b = test.set_index(["store_id", "time_slot", "date"])[[]].join(baseline).reset_index()
y_base = daily_total(b, TARGETS).reindex(y_true.index)

# 한계선: 잡음 없는 기대값 자체를 예측값으로 쓴 경우
y_floor = daily_total(exp[exp["date"] >= TEST_START], TARGETS).reindex(y_true.index)

# 학습 기간별 모델
rows, models_by_m, preds_by_m, train_by_m = [], {}, {}, {}
for m in MONTHS:
    start = pd.Timestamp(TRAIN_END) - pd.DateOffset(months=m) + pd.Timedelta(days=1)
    train = s[(s["date"] >= start) & (s["date"] <= TRAIN_END)]
    models = {t: fit(train, t) for t in TARGETS}
    p = test[["store_id", "date"]].join(predict(models, test))
    y_hat = daily_total(p, TARGETS).reindex(y_true.index)
    models_by_m[m], preds_by_m[m], train_by_m[m] = models, y_hat, train
    rows.append(dict(train_months=m, model=wmape(y_true, y_hat), baseline=wmape(y_true, y_base), floor=wmape(y_true, y_floor)))
curve = pd.DataFrame(rows)
print("검증기간(2026) 가게·일 매출 오차 WMAPE:\n", (curve.set_index("train_months") * 100).round(1).to_string())

best = preds_by_m[36]
per_store = pd.DataFrame({"model": [wmape(y_true.loc[k], best.loc[k]) for k in y_true.index.levels[0]],
                          "baseline": [wmape(y_true.loc[k], y_base.loc[k]) for k in y_true.index.levels[0]],
                          "floor": [wmape(y_true.loc[k], y_floor.loc[k]) for k in y_true.index.levels[0]]},
                         index=y_true.index.levels[0])
print("\n가게별 WMAPE(%, 36개월 학습):\n", (per_store * 100).round(1).to_string())


# 모델이 스스로 찾은 효과: 검증기간에서 요인만 켜고 끈 예측 비교
# 트리 모델은 겪어본 적 없는 상황을 추정 못 함 → 학습 기간에 그 요인을 1번 이상 겪은 가게만 대상
# (예: 축제 +25%는 3년간 S1·S2·S5에만 33일. 전 가게에 켜면 17.6%로 과소추정됨)
def effect(m, target, col, on, off):
    models, train = models_by_m[m], train_by_m[m]
    seen = train.loc[train[col] == on, "store_id"].unique()
    if len(seen) == 0:
        return 0.0  # 한 번도 겪지 않은 요인은 효과를 배울 수 없음
    base = test[test["store_id"].isin(seen)]
    X_on, X_off = base.copy(), base.copy()
    X_on[col], X_off[col] = on, off
    if col == "is_heat":
        X_on["is_cold"] = X_off["is_cold"] = 0
    one = {target: models[target]}  # predict()가 가게×시간대 offset까지 더해줌
    return float(predict(one, X_on)[target].sum() / predict(one, X_off)[target].sum() - 1)


FACTORS = [("비 → 홀", "sales_hall", "is_rain", True, False, -0.20), ("비 → 배달", "sales_delivery", "is_rain", True, False, 0.30),
           ("폭염 → 홀", "sales_hall", "is_heat", True, False, -0.10), ("축제 1km", "sales_hall", "fest_mult", 1.25, 1.0, 0.25),
           ("경쟁점 1곳", "sales_hall", "n_comp_90d", 1, 0, -0.03)]
eff = pd.DataFrame([dict(factor=name, truth=truth, m3=effect(3, t, c, on, off), m36=effect(36, t, c, on, off))
                    for name, t, c, on, off, truth in FACTORS])
print("\n요인 효과(%): 규칙 vs 모델이 찾은 값\n", (eff.set_index("factor") * 100).round(1).to_string())

# ---- 검증 기준 (실패하면 멈춤) ----
assert curve.set_index("train_months").loc[36, "model"] < curve.loc[0, "baseline"], "모델이 기준선보다 못함"
assert curve["model"].iloc[-1] <= curve["model"].iloc[0], "데이터가 늘었는데 오차가 줄지 않음"
assert (per_store["model"] < per_store["baseline"]).all(), "기준선을 못 이긴 가게가 있음"
for r in eff.itertuples():
    assert abs(r.m36 - r.truth) < 0.05, f"{r.factor} 효과를 제대로 못 찾음"
print("\n검증 통과")

out_tbl = curve.assign(kind="learning_curve")
pd.concat([out_tbl, per_store.reset_index().assign(kind="per_store"), eff.assign(kind="effects")]).to_csv(
    data / "sales_model_validation.csv", index=False, encoding="utf-8-sig")

# ---------------- 그래프 ----------------
pct = matplotlib.ticker.PercentFormatter(1.0, decimals=0)

# 1) 실제 vs 예측 (검증기간, S1·S4)
fig, axes = plt.subplots(2, 1, figsize=(11, 6.4), sharex=True)
for ax, sid, title in zip(axes, ["S1", "S4"], ["S1 치킨집 (달서구 두류1,2동)", "S4 고깃집 (남구 대명3동)"]):
    yt, yp = y_true.loc[sid] / 1e4, best.loc[sid] / 1e4
    ax.plot(yt.index, yt.values, color=MUTED, lw=1, label="실제(가상) 매출")
    ax.plot(yp.index, yp.values, color=BLUE, lw=2, label="모델 예측")
    ax.set_title(f"{title}   오차 {wmape(y_true.loc[sid], best.loc[sid]):.1%}  ·  사장님 감 {wmape(y_true.loc[sid], y_base.loc[sid]):.1%}", loc="left")
    ax.set_ylabel("일 매출(만원)")
    if sid == "S1":
        ax.axvspan(pd.Timestamp("2026-07-01"), pd.Timestamp("2026-07-05"), color=BLUE, alpha=0.08, lw=0)
        ax.annotate("치맥페스티벌", (pd.Timestamp("2026-07-03"), yt.max()), color=INK2, fontsize=9, ha="center", va="bottom")
axes[0].legend(loc="upper left", ncol=2)
fig.suptitle("본 적 없는 2026년 매출을 얼마나 맞히나", x=0.01, ha="left", fontsize=14, fontweight="bold", color=INK)
fig.text(0.01, 0.005, "학습 2023-01~2025-12 · 검증 2026-01~09 · 입력: 요일·휴일·날씨·축제일정·경쟁점 개업 · 매출은 규칙 기반 가상 데이터",
         fontsize=8, color=MUTED)
fig.tight_layout(rect=(0, 0.02, 1, 0.97))
fig.savefig(figs / "sales_model_1_actual_vs_pred.png", dpi=150)

# 2) 가게별 오차: 모델 vs 사장님 감
fig, ax = plt.subplots(figsize=(8, 4.2))
yy = np.arange(len(per_store))
ax.barh(yy - 0.21, per_store["baseline"], height=0.38, color=ORANGE, label="사장님 감 (최근 4주 같은 요일 평균)")
ax.barh(yy + 0.21, per_store["model"], height=0.38, color=BLUE, label="모델")
for i, (bv, mv) in enumerate(zip(per_store["baseline"], per_store["model"])):
    ax.text(bv + 0.003, i - 0.21, f"{bv:.1%}", va="center", fontsize=8, color=INK2)
    ax.text(mv + 0.003, i + 0.21, f"{mv:.1%}", va="center", fontsize=8, color=INK2)
ax.set_yticks(yy, per_store.index)
ax.invert_yaxis()
ax.xaxis.set_major_formatter(pct)
ax.set_xlabel("일 매출 오차 (WMAPE, 낮을수록 정확)")
ax.set_title("6개 가게 모두 모델이 더 정확", loc="left", pad=28)
ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2, fontsize=9)
ax.grid(axis="y", visible=False)
fig.tight_layout()
fig.savefig(figs / "sales_model_2_error_by_store.png", dpi=150)

# 3) 학습 데이터가 쌓일수록
fig, ax = plt.subplots(figsize=(8, 4.4))
ax.plot(curve["train_months"], curve["model"], color=BLUE, lw=2, marker="o", ms=7, label="모델")
ax.axhline(curve["baseline"].iloc[0], color=ORANGE, lw=2, label="사장님 감")
ax.axhline(curve["floor"].iloc[0], color=MUTED, lw=1, label="도달 가능한 한계 (우연한 변동)")
for x, v in zip(curve["train_months"], curve["model"]):
    near_base = abs(v - curve["baseline"].iloc[0]) < 0.005  # 기준선에 걸리면 오른쪽으로
    ax.annotate(f"{v:.1%}", (x, v), textcoords="offset points", xytext=(22, 4) if near_base else (0, 8),
                ha="center", fontsize=9, color=INK2)
ax.set_xticks(MONTHS, [f"{m}개월" for m in MONTHS])
ax.yaxis.set_major_formatter(pct)
ax.set_ylim(0, max(curve["model"].max(), curve["baseline"].iloc[0]) * 1.25)
ax.set_xlabel("학습에 쓴 매출 데이터 기간")
ax.set_ylabel("2026년 일 매출 오차")
ax.set_title("매출 데이터가 쌓일수록 정확해진다", loc="left")
ax.legend(loc="upper right", fontsize=9)
fig.tight_layout()
fig.savefig(figs / "sales_model_3_learning_curve.png", dpi=150)

# 4) 모델이 찾아낸 요인 효과
fig, ax = plt.subplots(figsize=(8.5, 4.4))
xx = np.arange(len(eff))
ax.bar(xx - 0.27, eff["truth"], width=0.25, color=MUTED, label="숨겨둔 실제 규칙")
ax.bar(xx, eff["m3"], width=0.25, color=ORANGE, label="3개월 학습")
ax.bar(xx + 0.27, eff["m36"], width=0.25, color=BLUE, label="36개월 학습")
for i, v in enumerate(eff["m3"]):
    if v == 0:
        ax.text(i, 0.01, "겪은 적\n없음", ha="center", va="bottom", fontsize=8, color=MUTED)
ax.axhline(0, color=AXIS, lw=1)
ax.set_xticks(xx, eff["factor"])
ax.yaxis.set_major_formatter(pct)
ax.set_ylabel("매출 변화")
ax.set_title("모델이 날씨·축제·경쟁점의 영향을 스스로 찾아냄", loc="left", pad=28)
ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), fontsize=9, ncol=3)
ax.grid(axis="x", visible=False)
fig.text(0.01, 0.005, "효과 = 검증기간 예측에서 해당 요인만 켜고 끈 차이 · 학습 기간에 그 요인을 겪은 가게 기준 · 3개월(2025-10~12)엔 폭염·큰 축제가 없었음",
         fontsize=8, color=MUTED)
fig.tight_layout(rect=(0, 0.03, 1, 1))
fig.savefig(figs / "sales_model_4_effects.png", dpi=150)
print("그래프 저장:", sorted(p.name for p in figs.glob("sales_model_*.png")))
