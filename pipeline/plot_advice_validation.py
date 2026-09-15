"""LLM 조언 검증 결과 그래프.

usage: py -X utf8 plot_advice_validation.py data/processed/advice docs/figures
① 가게×기준일별 검증(지어낸 숫자·날짜, 빠진 신호, 재시도) ② 과거 재현(6/29) 조언 뒤 실제로 일어난 일
"""
import json
import sys
from pathlib import Path

import matplotlib
import pandas as pd

matplotlib.use("Agg")
import matplotlib.pyplot as plt

src, figs = Path(sys.argv[1]), Path(sys.argv[2])
recs = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(src.glob("*/S*.json"))]

BLUE, INK, INK2, MUTED, GRID, AXIS, SURF = "#2a78d6", "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#c3c2b7", "#fcfcfb"
GOOD, CRIT = "#0ca30c", "#d03b3b"
plt.rcParams.update({"font.family": ["Malgun Gothic", "Segoe UI Symbol"], "axes.facecolor": SURF, "figure.facecolor": SURF, "axes.edgecolor": AXIS,
                     "axes.labelcolor": INK2, "xtick.color": MUTED, "ytick.color": MUTED, "axes.titlecolor": INK,
                     "axes.titlesize": 12, "axes.titleweight": "bold", "axes.unicode_minus": False})

# ① 검증 표
cols = ["지어낸 숫자", "지어낸 날짜", "빠진 신호", "키 노출", "시도 횟수"]
rows, labels = [], []
for r in recs:
    last = r["validation"][-1]
    rows.append([len(last["invented_numbers"]), len(last["invented_dates"]), len(last["missing_signals"]), len(last.get("format_issues", [])), len(r["validation"])])
    labels.append(f"{r['as_of'][5:]} {r['signals']['store']['id']} {r['signals']['store']['업종']}")
fig, ax = plt.subplots(figsize=(8.5, 0.42 * len(rows) + 1.6))
for i, row in enumerate(rows):
    for j, v in enumerate(row):
        ok = v == 0 if j < 4 else v == 1
        mark = "✓" if ok else "✗"
        text = f"{mark} {v}" if j < 4 else (f"{mark} 1회" if v == 1 else f"↻ {v}회")
        color = GOOD if ok else (CRIT if j < 4 else INK2)
        ax.text(j, i, text, ha="center", va="center", fontsize=10, color=color, fontweight="bold")
ax.set_xlim(-0.5, len(cols) - 0.5)
ax.set_ylim(len(rows) - 0.5, -0.5)
ax.set_xticks(range(len(cols)), cols)
ax.xaxis.tick_top()
ax.set_yticks(range(len(rows)), labels)
for y in range(len(rows) + 1):
    ax.axhline(y - 0.5, color=GRID, lw=0.6)
for s in ax.spines.values():
    s.set_visible(False)
ax.tick_params(length=0)
passed = sum(r["validation"][-1]["passed"] for r in recs)
first = sum(r["validation"][0]["passed"] for r in recs)
ax.set_title(f"LLM 조언 검증: {passed}/{len(recs)} 통과 (첫 시도 {first}/{len(recs)})", loc="left", pad=34)
fig.text(0.01, 0.01, f"모델 {recs[0]['model']} · 신호에 없는 숫자·날짜를 쓰거나 필수 신호를 빠뜨리면 자동 재생성(최대 3회)",
         fontsize=8, color=MUTED)
fig.tight_layout(rect=(0, 0.03, 1, 1))
fig.savefig(figs / "advice_1_validation.png", dpi=150)

# ② 과거 재현: 조언 뒤 실제 매출 변화
rep = [r for r in recs if r["mode"] == "replay"]
if rep:
    fig, ax = plt.subplots(figsize=(8.5, 4.2))
    names, vals, notes = [], [], []
    for r in rep:
        sig = r["signals"]
        names.append(f"{sig['store']['id']} {sig['store']['업종']}")
        vals.append(r["actual_after"]["다음7일_일평균매출_변화_퍼센트"] / 100)
        tags = [f"축제 {f['거리_m']}m" for f in sig["festivals_next7"] if f["거리_m"] <= 1000]
        tags += [f"경쟁점 {c['경과일']}일 전 개업" for c in sig["competitors_90d"]]
        notes.append(" · ".join(tags) or "특이 신호 없음")
    yy = range(len(names))
    ax.barh(list(yy), vals, color=BLUE, height=0.6)
    ax.axvline(0, color=AXIS, lw=1)
    for i, (v, n) in enumerate(zip(vals, notes)):
        ax.text(v + (0.005 if v >= 0 else -0.005), i, f"{v:+.0%}  {n}", va="center", ha="left" if v >= 0 else "right", fontsize=9, color=INK2)
    ax.set_yticks(list(yy), names)
    ax.invert_yaxis()
    ax.xaxis.set_major_formatter(matplotlib.ticker.PercentFormatter(1.0, decimals=0))
    lim = max(abs(min(vals)), abs(max(vals))) * 2.2
    ax.set_xlim(-lim, lim)
    ax.grid(axis="x", color=GRID, lw=0.6)
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_xlabel("조언 뒤 7일 일평균 매출 (직전 4주 대비)")
    ax.set_title(f"사후 확인: {rep[0]['as_of']} 조언 뒤 실제로 일어난 일", loc="left")
    fig.text(0.01, 0.01, "매출은 규칙 기반 가상 데이터 · 날씨·축제·경쟁점·식자재 가격은 실제 데이터", fontsize=8, color=MUTED)
    fig.tight_layout(rect=(0, 0.03, 1, 1))
    fig.savefig(figs / "advice_2_replay_outcome.png", dpi=150)
print("저장:", sorted(p.name for p in figs.glob("advice_*.png")))
