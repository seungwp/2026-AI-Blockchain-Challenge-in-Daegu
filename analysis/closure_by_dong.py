"""대구 인허가(일반음식점) → 행정동 × 분기 개업·폐업·폐업률 집계.

usage: py -X utf8 closure_by_dong.py data/raw/licenses/daegu_restaurants.csv data/raw/boundary/daegu_dong_ver20250401.geojson data/processed
(공공데이터포털 행정안전부_식품_일반음식점 대구 파일에서 8개 컬럼만 추출한 것)
폐업률 = 분기 중 폐업 수 / 분기 시작 시점 영업 점포 수
"""
import json, sys
from pathlib import Path

import numpy as np
import pandas as pd
from matplotlib.path import Path as MplPath
from pyproj import Transformer

xlsx, geojson, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
out.mkdir(parents=True, exist_ok=True)

cols = ["관리번호", "인허가일자", "폐업일자", "영업상태명", "업태구분명",
        "소재지전체주소", "좌표정보X(EPSG5174)", "좌표정보Y(EPSG5174)"]
df = pd.read_csv(xlsx, usecols=cols, dtype=str) if xlsx.endswith(".csv") else pd.read_excel(xlsx, usecols=cols)
date = lambda s: pd.to_datetime(s.astype(str).str.replace("-", "").str[:8], format="%Y%m%d", errors="coerce")
df["open"], df["close"] = date(df["인허가일자"]), date(df["폐업일자"])
print("rows", len(df), "| 폐업상태인데 폐업일자 없음", ((df["영업상태명"] == "폐업") & df["close"].isna()).sum())

# 좌표 → 행정동 (point-in-polygon)
x = pd.to_numeric(df["좌표정보X(EPSG5174)"], errors="coerce")
y = pd.to_numeric(df["좌표정보Y(EPSG5174)"], errors="coerce")
lon, lat = Transformer.from_crs("EPSG:5174", "EPSG:4326", always_xy=True).transform(x.values, y.values)
pts = np.column_stack([lon, lat])
df["adm_cd"], df["adm_nm"] = None, None
feats = [f for f in json.load(open(geojson, encoding="utf-8"))["features"] if f["properties"]["sidonm"] == "대구광역시"]
valid = np.isfinite(pts).all(axis=1)
for f in feats:
    polys = f["geometry"]["coordinates"] if f["geometry"]["type"] == "MultiPolygon" else [f["geometry"]["coordinates"]]
    hit = np.zeros(len(df), bool)
    for poly in polys:
        # ponytail: 외곽 링만 사용(구멍 무시), 행정동 경계 수준에선 충분
        hit[valid] |= MplPath(np.array(poly[0])).contains_points(pts[valid])
    df.loc[hit, ["adm_cd", "adm_nm"]] = f["properties"]["adm_cd2"], f["properties"]["adm_nm"].replace("대구광역시 ", "")
print("행정동 매칭률", round(df["adm_nm"].notna().mean() * 100, 1), "%")
df.to_csv(out / "stores_with_dong.csv", index=False, encoding="utf-8-sig")

# 월별 시 전체 폐업 수 + 직권폐업 의심 급증 월 (중앙값 + 3*MAD)
m = df.dropna(subset=["close"]).groupby(df["close"].dt.to_period("M")).size()
m = m[m.index >= "2016-01"]
med, mad = m.median(), (m - m.median()).abs().median()
spikes = m[m > med + 3 * 1.4826 * mad]
m.rename("폐업수").to_csv(out / "monthly_closures.csv", encoding="utf-8-sig")
print("\n월별 폐업 중앙값", med, "| 급증 의심 월:\n", spikes.to_string())

# 행정동 × 분기 패널
d = df.dropna(subset=["adm_nm", "open"])
rows = []
for q in pd.period_range("2016Q1", "2026Q2", freq="Q"):
    s, e = q.start_time, q.end_time
    alive = (d["open"] < s) & (d["close"].isna() | (d["close"] >= s))
    g = pd.DataFrame({"adm_nm": d["adm_nm"], "stock": alive,
                      "opened": d["open"].between(s, e), "closed": d["close"].between(s, e)})
    agg = g.groupby("adm_nm")[["stock", "opened", "closed"]].sum()
    agg["quarter"] = str(q)
    rows.append(agg.reset_index())
panel = pd.concat(rows)
panel["closure_rate"] = (panel["closed"] / panel["stock"].where(panel["stock"] > 0)).round(4)
panel["net"] = panel["opened"] - panel["closed"]
panel.to_csv(out / "dong_quarter_closure.csv", index=False, encoding="utf-8-sig")

# 연간 요약: 점포 50개 이상 동
panel["year"] = panel["quarter"].str[:4]
yr = panel.groupby(["adm_nm", "year"]).agg(stock=("stock", "first"), closed=("closed", "sum"), opened=("opened", "sum"))
yr["rate"] = (yr["closed"] / yr["stock"]).round(3)
yr = yr.reset_index()
yr.to_csv(out / "dong_year_closure.csv", index=False, encoding="utf-8-sig")
big = yr[yr["stock"] >= 50]
w = big.pivot(index="adm_nm", columns="year", values="rate")
print("\n시 전체 연간 폐업률:\n", yr.groupby("year").apply(lambda t: round(t.closed.sum() / t.stock.sum(), 3)).to_string())
print("\n2024 폐업률 상위 15개 동:\n", big[big.year == "2024"].nlargest(15, "rate")[["adm_nm", "stock", "opened", "closed", "rate"]].to_string(index=False))
print("\n2019→2024 폐업률 상승폭 상위 15개 동:\n", (w["2024"] - w["2019"]).dropna().nlargest(15).round(3).to_string())
ind = "신당동|이곡|용산|장기동|노원동|침산|이현동|평리|비산7동|논공|구지|유가|현풍"
print("\n산단 인근 동 연간 폐업률:\n", w[w.index.str.contains(ind)][[str(y) for y in range(2018, 2026)]].to_string())
