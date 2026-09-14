"""대구 산단 분기 고용·가동업체 → 배후 행정동 폐업률 선행성 확인.

usage: py -X utf8 sandan_vs_closure.py data/raw/sandan data/raw/boundary/daegu_dong_ver20250401.geojson data/processed
"""
import io, json, re, sys, zipfile, warnings
from pathlib import Path

import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")
src, geojson, out = Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3])

# ponytail: 산단 대표좌표는 수기 근사치(단지 중심 ±1km), 정밀화는 산단 경계 SHP로
CLUSTERS = {
    "성서산단": (["성서1차", "성서2차", "성서3차", "성서4차"], 128.500, 35.842),
    "제3산단": (["대구제3"], 128.583, 35.897),
    "서대구산단": (["서대구"], 128.545, 35.880),
    "염색산단": (["대구염색"], 128.567, 35.884),
    "검단산단": (["검단"], 128.617, 35.912),
    "달성1차": (["달성1차"], 128.437, 35.738),
    "달성2차·국가산단": (["달성2차", "대구국가"], 128.430, 35.660),
}
RADIUS_KM = 2.5


def open_book(p):
    z = zipfile.ZipFile(p)
    inner = [n for n in z.namelist() if n.lower().endswith((".xlsx", ".xls"))]
    return pd.ExcelFile(io.BytesIO(z.read(inner[0]))) if inner else pd.ExcelFile(p)


rows = []
for p in sorted(src.glob("*.bin")):
    try:
        v = open_book(p).parse("전국산업단지현황", header=None).values
    except Exception as e:
        print("skip", p.name, type(e).__name__)
        continue
    m = re.search(r"(20\d\d)년\s*(\d)분기", str(v[0, 0]))
    if not m or v.shape[1] < 19:
        print("skip(no 고용/생산 cols)", p.name)
        continue
    for r in v:
        if str(r[1]) != "대구":
            continue
        name = str(r[3])
        for c, (keys, *_ ) in CLUSTERS.items():
            if any(name.startswith(k) or name.strip().startswith(k) for k in keys) and "①" not in name and "②" not in name:
                num = lambda x: pd.to_numeric(str(x).replace(",", ""), errors="coerce")
                rows.append(dict(cluster=c, complex=name.strip(), year=int(m[1]), q=int(m[2]),
                                 firms=num(r[13]), emp=num(r[16]), prod_cum=num(r[17])))
s = pd.DataFrame(rows).drop_duplicates(["complex", "year", "q"])
s = s.groupby(["cluster", "year", "q"], as_index=False)[["firms", "emp", "prod_cum"]].sum(min_count=1)
s = s.sort_values(["cluster", "year", "q"])
# 누계생산 → 분기생산 (연초 리셋)
s["prod"] = s.groupby(["cluster", "year"])["prod_cum"].diff().fillna(s["prod_cum"])
s["quarter"] = s["year"].astype(str) + "Q" + s["q"].astype(str)
for col in ["emp", "prod", "firms"]:
    s[f"{col}_yoy"] = s.groupby("cluster")[col].pct_change(4)  # 결측 분기 있으면 NaN
s.to_csv(out / "sandan_cluster_quarter.csv", index=False, encoding="utf-8-sig")
print("\n분기 커버리지:", s["quarter"].min(), "~", s["quarter"].max(), "|", s["quarter"].nunique(), "개 분기")

# 배후 행정동: 행정동 중심점이 산단 좌표 반경 RADIUS_KM 이내
feats = [f for f in json.load(open(geojson, encoding="utf-8"))["features"] if f["properties"]["sidonm"] == "대구광역시"]
cent = {}
for f in feats:
    g = f["geometry"]["coordinates"]
    ring = np.array((g[0][0] if f["geometry"]["type"] == "MultiPolygon" else g[0]))
    cent[f["properties"]["adm_nm"].replace("대구광역시 ", "")] = ring.mean(axis=0)
km = lambda a, b: np.hypot((a[0] - b[0]) * 91.3, (a[1] - b[1]) * 111.0)
catch = {c: [d for d, xy in cent.items() if km(xy, (lon, lat)) <= RADIUS_KM] for c, (_, lon, lat) in CLUSTERS.items()}
for c, ds in catch.items():
    print(f"{c} 배후동({len(ds)}): {', '.join(ds)}")

panel = pd.read_csv(out / "dong_quarter_closure.csv")
city = panel.groupby("quarter")[["closed", "stock"]].sum()
city_rate = city["closed"] / city["stock"]
res, series = [], []
for c, ds in catch.items():
    t = panel[panel["adm_nm"].isin(ds)].groupby("quarter")[["closed", "stock"]].sum()
    # 시 전체 대비 초과 폐업률 → 공통 경기요인 제거, 4분기 이동합으로 계절성/12월 정비 완화
    excess = (t["closed"] / t["stock"] - city_rate).rolling(4).mean()
    x = s[s["cluster"] == c].set_index("quarter")
    df = pd.DataFrame({"emp_yoy": x["emp_yoy"], "prod_yoy": x["prod_yoy"], "firms_yoy": x["firms_yoy"]}).join(excess.rename("excess"), how="inner")
    df["cluster"] = c
    series.append(df.reset_index())
    for var in ["emp_yoy", "prod_yoy", "firms_yoy"]:
        for lag in range(0, 5):
            pair = pd.concat([df[var], df["excess"].shift(-lag)], axis=1).dropna()
            if len(pair) >= 8:
                res.append(dict(cluster=c, var=var, lag_q=lag, n=len(pair), corr=round(pair.iloc[:, 0].corr(pair.iloc[:, 1]), 3)))
pd.concat(series).to_csv(out / "sandan_catchment_series.csv", index=False, encoding="utf-8-sig")
r = pd.DataFrame(res)
r.to_csv(out / "sandan_lag_corr.csv", index=False, encoding="utf-8-sig")
print("\n산단지표 YoY vs k분기 뒤 배후동 초과폐업률 상관 (음수 = 산단 악화 → 폐업 증가):")
print(r.pivot_table(index=["cluster", "var"], columns="lag_q", values="corr").round(2).to_string())
print("\n표본수(n) 범위:", r["n"].min(), "~", r["n"].max())
