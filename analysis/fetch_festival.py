"""TourAPI(KorService2 searchFestival2)로 대구 행사 수집 + 좌표 → 행정동 매칭.

usage: py -X utf8 fetch_festival.py 20260101 data/raw/boundary/daegu_dong_ver20250401.geojson data/raw/festival
.env 에 DATA_GO_KR_KEY 필요 (Encoding/Decoding 키 둘 다 가능)
"""
import json, sys, urllib.parse, urllib.request
from pathlib import Path

import numpy as np
import pandas as pd
from matplotlib.path import Path as MplPath

start, geojson, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
env = dict(l.strip().split("=", 1) for l in open(".env", encoding="utf-8-sig") if "=" in l and not l.startswith("#"))
key = urllib.parse.unquote(env["DATA_GO_KR_KEY"])

rows, page = [], 1
while True:
    q = dict(serviceKey=key, MobileOS="ETC", MobileApp="dongne", _type="json", numOfRows="100", pageNo=page,
             eventStartDate=start, lDongRegnCd="27")  # 27 = 대구 (areaCode=4는 KorService2에서 0건)
    body = json.loads(urllib.request.urlopen(
        "https://apis.data.go.kr/B551011/KorService2/searchFestival2?" + urllib.parse.urlencode(q), timeout=60).read())["response"]["body"]
    items = body["items"]["item"] if body["items"] else []
    rows += items
    if not items or len(rows) >= body["totalCount"]:
        break
    page += 1

df = pd.DataFrame(rows)[["contentid", "title", "eventstartdate", "eventenddate", "addr1", "mapx", "mapy", "tel", "lclsSystm3"]]
df = df.rename(columns={"eventstartdate": "start", "eventenddate": "end", "addr1": "addr", "mapx": "lon", "mapy": "lat", "lclsSystm3": "category"})
for c in ["start", "end"]:
    df[c] = pd.to_datetime(df[c], format="%Y%m%d").dt.date
df[["lon", "lat"]] = df[["lon", "lat"]].apply(pd.to_numeric, errors="coerce")

pts = df[["lon", "lat"]].to_numpy()
df["adm_nm"] = None
for f in json.load(open(geojson, encoding="utf-8"))["features"]:
    polys = f["geometry"]["coordinates"] if f["geometry"]["type"] == "MultiPolygon" else [f["geometry"]["coordinates"]]
    hit = np.zeros(len(df), bool)
    for poly in polys:
        hit |= MplPath(np.array(poly[0])).contains_points(pts)
    df.loc[hit, "adm_nm"] = f["properties"]["adm_nm"].replace("대구광역시 ", "")

df = df.sort_values("start")
df.to_csv(out / "daegu_festival_tourapi.csv", index=False, encoding="utf-8-sig")
print(f"{len(df)}건 | 기간 {df['start'].min()} ~ {df['end'].max()} | 행정동 매칭 {df['adm_nm'].notna().sum()}건")
print(df[["title", "start", "end", "adm_nm"]].to_string(index=False))
