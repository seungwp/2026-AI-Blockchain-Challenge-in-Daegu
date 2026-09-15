"""KAMIS 17번 API(periodRetailProductList)로 대구 소매가격 일별 수집.

usage: py -X utf8 fetch_kamis.py data/raw/kamis
.env 에 KAMIS_CERT_KEY, KAMIS_CERT_ID 필요
"""
import json, os, sys, time, urllib.parse, urllib.request
from pathlib import Path

import pandas as pd

out = Path(sys.argv[1])
env = dict(l.strip().split("=", 1) for l in open(".env", encoding="utf-8-sig") if "=" in l and not l.startswith("#"))
KEY, ID = env["KAMIS_CERT_KEY"], env["KAMIS_CERT_ID"]

# 품목명: (부류, 품목, 품종, 등급)  코드는 kamis_codes.xlsx 기준, 등급 04=상품
# ponytail: 축산물(삼겹살·닭·계란)은 축평원 데이터라 17번에서 안 나올 수 있음, 첫 호출로 확인
ITEMS = {
    "배추": ("200", "211", "", "04"),
    "무": ("200", "231", "", "04"),
    "양파": ("200", "245", "00", "04"),
    "대파": ("200", "246", "00", "04"),
    "깐마늘": ("200", "258", "01", "04"),
    "삼겹살": ("500", "4304", "27", "00"),
    "닭": ("500", "9901", "99", "00"),
    "계란": ("500", "9903", "23", "71"),
}
PERIODS = [("2023-01-01", "2023-12-31"), ("2024-01-01", "2024-12-31"),
           ("2025-01-01", "2025-12-31"), ("2026-01-01", "2026-09-13")]


def call(cat, item, kind, rank, start, end):
    q = dict(action="periodRetailProductList", p_cert_key=KEY, p_cert_id=ID, p_returntype="json",
             p_startday=start, p_endday=end, p_itemcategorycode=cat, p_itemcode=item,
             p_kindcode=kind, p_productrankcode=rank, p_countrycode="2200", p_convert_kg_yn="N")
    url = "http://www.kamis.or.kr/service/price/xml.do?" + urllib.parse.urlencode(q)
    return json.loads(urllib.request.urlopen(url, timeout=60).read().decode("utf-8"))


rows = []
for name, codes in ITEMS.items():
    for start, end in PERIODS:
        j = call(*codes, start, end)
        data = j.get("data", {})
        items = data.get("item", []) if isinstance(data, dict) else []
        code = data.get("error_code") if isinstance(data, dict) else data
        print(f"{name} {start[:4]}: {len(items)}건 (code={code})")
        # 응답 = 평균(대구 시장 평균) + 평년 + 시장·품종별 행. 평균·평년만 사용
        for it in items:
            if it["countyname"] in ("평균", "평년"):
                rows.append(dict(item=name, date=f"{it['yyyy']}-{it['regday'].replace('/', '-')}",
                                 kind=it["countyname"], price=it["price"]))
        time.sleep(0.5)

df = pd.DataFrame(rows)
if df.empty:
    sys.exit("데이터 없음 - 응답 확인 필요:\n" + json.dumps(j, ensure_ascii=False)[:1000])
df["price"] = pd.to_numeric(df["price"].str.replace(",", ""), errors="coerce")
df = df.pivot_table(index=["item", "date"], columns="kind", values="price").reset_index()
df = df.rename(columns={"평균": "price", "평년": "normal_price"})
df.to_csv(out / "daegu_retail_daily.csv", index=False, encoding="utf-8-sig")
print(df.groupby("item").agg(days=("date", "size"), start=("date", "min"), end=("date", "max"),
                             missing=("price", lambda s: s.isna().sum())).to_string())
