"""기상청 ASOS 일자료(대구 143)를 기존 CSV 뒤에 이어 붙인다.

usage: py -X utf8 pipeline/fetch_asos.py data/raw/weather/daegu_asos143_daily_20230101_20260913.csv
.env 의 DATA_GO_KR_KEY 필요. 파일의 마지막 날짜 다음날부터 어제까지 받아온다(당일 자료는 미확정).
"""
import sys
from pathlib import Path

import pandas as pd
import requests

csv_path = Path(sys.argv[1])
env = dict(l.strip().split("=", 1) for l in open(".env", encoding="utf-8-sig") if "=" in l and not l.startswith("#"))
key = requests.utils.unquote(env["DATA_GO_KR_KEY"])

old = pd.read_csv(csv_path, encoding="cp949")
last = pd.Timestamp(old["일시"].max())
start, end = last + pd.Timedelta(days=1), pd.Timestamp.today().normalize() - pd.Timedelta(days=1)
if start > end:
    sys.exit(f"이미 최신입니다 (마지막 {last.date()})")

res = requests.get("https://apis.data.go.kr/1360000/AsosDalyInfoService/getWthrDataList", timeout=60, params={
    "serviceKey": key, "pageNo": 1, "numOfRows": 100, "dataType": "JSON",
    "dataCd": "ASOS", "dateCd": "DAY", "stnIds": 143,
    "startDt": start.strftime("%Y%m%d"), "endDt": end.strftime("%Y%m%d"),
})
body = res.json()["response"]["body"]
rows = body["items"]["item"]
new = pd.DataFrame([{
    "지점": 143, "지점명": "대구", "일시": r["tm"],
    "평균기온(°C)": r["avgTa"], "최저기온(°C)": r["minTa"],
    "최고기온(°C)": r["maxTa"], "일강수량(mm)": r["sumRn"],
} for r in rows])
new = new.replace("", pd.NA)

merged = pd.concat([old, new], ignore_index=True).drop_duplicates(subset="일시", keep="last").sort_values("일시")
merged.to_csv(csv_path, index=False, encoding="cp949")
print(f"{start.date()} ~ {end.date()} {len(new)}일 추가 → 총 {len(merged)}일 (마지막 {merged['일시'].max()})")
