"""데모용 가상 가게 6곳 선정 (실제 인허가 점포에 역할 부여, 매출만 가상).

usage: py -X utf8 select_demo_stores.py data/processed/stores_with_dong.csv data/raw/festival/daegu_festival_tourapi.csv data/processed
화면·자료에는 store_id·업종·행정동만 표시 (실존 가게라 주소·관리번호 비노출)
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from pyproj import Transformer

stores_csv, fest_csv, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
DEMO_DATE = pd.Timestamp("2026-09-14")  # 인허가 데이터 갱신일
RECENT = DEMO_DATE - pd.Timedelta(days=90)
SEED = 42
GROUP = {"통닭(치킨)": "치킨", "호프/통닭": "치킨", "한식": "한식", "식육(숯불구이)": "고깃집", "분식": "분식", "중국식": "중국식"}

d = pd.read_csv(stores_csv, dtype=str)
d["open"], d["close"] = pd.to_datetime(d["open"]), pd.to_datetime(d["close"])
d["x"], d["y"] = pd.to_numeric(d["좌표정보X(EPSG5174)"]), pd.to_numeric(d["좌표정보Y(EPSG5174)"])  # TM 좌표 = 미터
d["group"] = d["업태구분명"].map(GROUP)
d["gu"] = d["adm_nm"].str.split().str[0]

# 경쟁점 개업: 축제 임시영업(주소에 '일원'/'축제', 30일 안에 폐업)은 제외
temp = d["소재지전체주소"].str.contains("일원|축제", na=False) | ((d["close"] - d["open"]).dt.days < 30)
openings = d[d["open"].between(RECENT, DEMO_DATE) & d["group"].notna() & ~temp & d["x"].notna()]

f = pd.read_csv(fest_csv)
f["x"], f["y"] = Transformer.from_crs("EPSG:4326", "EPSG:5174", always_xy=True).transform(f["lon"].values, f["lat"].values)

base = d[(d["영업상태명"] == "영업/정상") & (d["open"] < "2023-01-01") & d["group"].notna()
         & d["adm_nm"].notna() & d["x"].notna()].copy()
xy = base[["x", "y"]].to_numpy()


def near_count(points, radius, same_group=None):
    """base 각 점포 반경 radius(m) 안의 points 수 (same_group이면 업종 같은 것만)."""
    dist = np.hypot(xy[:, None, 0] - points["x"].to_numpy()[None], xy[:, None, 1] - points["y"].to_numpy()[None])
    hit = dist <= radius
    if same_group:
        hit &= base["group"].to_numpy()[:, None] == points["group"].to_numpy()[None]
    return hit.sum(axis=1)


def fest_dist(title):
    r = f[f["title"] == title].iloc[0]
    return np.hypot(xy[:, 0] - r["x"], xy[:, 1] - r["y"])


base["comp_500m_90d"] = near_count(openings, 500, same_group=True)
base["fest_1km_2026"] = near_count(f, 1000)
base["dist_chimaek"] = fest_dist("대구치맥페스티벌").round()
base["dist_maker"] = fest_dist("대구메이커페스타").round()

ROLES = [  # (store_id, 역할, 시나리오, 조건)
    ("S1", "치맥페스티벌 인근 치킨집", "①축제·날씨", lambda b: (b.group == "치킨") & (b.dist_chimaek <= 1000)),
    ("S2", "이번 주말 메이커페스타 인근 한식당", "①이번 주말 축제", lambda b: (b.group == "한식") & (b.dist_maker <= 1000)),
    ("S3", "최근 동종 개업 인근 치킨집", "③경쟁점", lambda b: (b.group == "치킨") & (b.comp_500m_90d >= 1)),
    ("S4", "삼겹살·배추 쓰는 고깃집", "②식자재", lambda b: b.group == "고깃집"),
    # 분식은 호떡·빙수까지 섞여 경쟁 관계가 모호해서 중국식으로 한정
    ("S5", "최근 동종 개업 인근 중국집", "③경쟁점", lambda b: (b.group == "중국식") & (b.comp_500m_90d >= 1)),
    # 대조군은 도시 가게끼리 비교해야 하므로 군위군·달성군(농촌 포함) 제외
    ("S6", "이벤트 없는 주거지 한식당(대조군)", "대조군", lambda b: (b.group == "한식") & (b.comp_500m_90d == 0)
     & (b.fest_1km_2026 == 0) & ~b.gu.isin(["군위군", "달성군"])),
]
rng = np.random.default_rng(SEED)
picked = []
for sid, role, scen, cond in ROLES:
    cand = base[cond(base) & ~base["관리번호"].isin([p["관리번호"] for p in picked])]
    used_gu = {p["gu"] for p in picked}
    spread = cand[~cand["gu"].isin(used_gu)]  # 가능하면 아직 안 뽑힌 구에서
    pool = spread if len(spread) else cand
    assert len(pool), f"{sid} 후보 없음"
    row = pool.iloc[rng.integers(len(pool))].to_dict()
    picked.append(dict(row, store_id=sid, role=role, scenario=scen, n_candidates=len(cand)))

cols = ["store_id", "role", "scenario", "group", "업태구분명", "gu", "adm_nm", "open", "n_candidates",
        "comp_500m_90d", "fest_1km_2026", "dist_chimaek", "dist_maker", "관리번호", "x", "y"]
res = pd.DataFrame(picked)[cols]
res["open"] = res["open"].dt.date
res.to_csv(out / "demo_stores.csv", index=False, encoding="utf-8-sig")
print(res.drop(columns=["관리번호", "x", "y"]).to_string(index=False))

# 검증: 역할 조건 재확인 (선정 로직과 별개로 원본에서 다시 계산)
chk = d.set_index("관리번호").loc[res["관리번호"]]
assert (chk["영업상태명"] == "영업/정상").all() and (chk["open"] < "2023-01-01").all()
r = res.set_index("store_id")
assert r.loc["S1", "dist_chimaek"] <= 1000 and r.loc["S2", "dist_maker"] <= 1000
assert r.loc["S3", "comp_500m_90d"] >= 1 and r.loc["S5", "comp_500m_90d"] >= 1
assert r.loc["S6", "comp_500m_90d"] == 0 and r.loc["S6", "fest_1km_2026"] == 0
assert res["관리번호"].is_unique
print("\n검증 통과 | 구 분포:", res["gu"].value_counts().to_dict())

# 경쟁점 상세 (S3·S5): 어떤 가게가 언제 열었는지
for sid in ["S3", "S5"]:
    s = r.loc[sid]
    o = openings[(openings["group"] == s["group"]) & (np.hypot(openings["x"] - s["x"], openings["y"] - s["y"]) <= 500)]
    print(f"{sid} 경쟁점:", [(str(t.open.date()), t.업태구분명, int(np.hypot(t.x - s["x"], t.y - s["y"]))) for t in o.itertuples()])
