"""데모 가게 6곳의 가상 매출 생성 (2023-01-01 ~ 2026-09-13, 가게 × 일 × 시간대).

usage: py -X utf8 make_virtual_sales.py data/processed [잡음sd=0.10] [출력파일=virtual_sales.csv]
       잡음 0으로 만든 virtual_sales_expected.csv는 모델 검증의 '도달 가능한 한계' 계산용

실제 매출이 아님. 규칙(docs/데이터관리_데모용.xlsx 가상매출 시트)에 공공데이터를 넣어 만든 값.
기본 매출·객단가·시간대 비중·식자재 사용량은 전부 가정값이며 실제 통계로 보정하지 않았음.
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from pyproj import Transformer

out = Path(sys.argv[1])
START, END, SEED = "2023-01-01", "2026-09-13", 42

# ---- 가정값 (업종별) ----
BASE_DAILY = {"치킨": 600_000, "한식": 700_000, "고깃집": 1_100_000, "중국식": 650_000}   # 평일 하루 매출(원)
TICKET = {"치킨": 22_000, "한식": 11_000, "고깃집": 45_000, "중국식": 15_000}             # 주문 1건당
SLOT_SHARE = {"치킨": (0.10, 0.55, 0.35), "한식": (0.45, 0.45, 0.10),                   # 점심·저녁·심야
              "고깃집": (0.15, 0.65, 0.20), "중국식": (0.50, 0.40, 0.10)}
DELIVERY_SHARE = {"치킨": 0.55, "한식": 0.15, "고깃집": 0.05, "중국식": 0.45}
PEAK_MONTH = {"치킨": 7, "한식": 10, "고깃집": 12, "중국식": 1}                             # 계절성 ±5%
HOLIDAY_SIGN = {"치킨": 1, "고깃집": 1, "한식": -1, "중국식": -1}                          # 가족 외식 +, 직장인 점심 -
USAGE = {  # 주문 1건당 KAMIS 단위 사용량 (배추 포기, 무 개, 양파·대파·깐마늘·닭 kg, 삼겹살 100g, 계란 30구)
    "치킨": {"닭": 1.0, "무": 0.2, "대파": 0.02},
    "한식": {"배추": 0.08, "무": 0.05, "양파": 0.05, "대파": 0.03, "깐마늘": 0.01, "계란": 0.02},
    "고깃집": {"삼겹살": 2.5, "배추": 0.1, "양파": 0.05, "대파": 0.03, "깐마늘": 0.02},
    "중국식": {"양파": 0.12, "대파": 0.03, "깐마늘": 0.01, "계란": 0.03, "삼겹살": 0.3},
}
# ---- 규칙 계수 (가상매출 시트) ----
RAIN_MM, HEAT_C, COLD_C = 5, 33, -5          # 한파는 -10°C면 4년간 5일뿐이라 -5°C로 완화
RAIN_HALL, RAIN_DELIV, TEMP_HALL = 0.80, 1.30, 0.90
FEST_1KM, FEST_2KM = 1.25, 1.10               # 시트의 '같은 동 +25%'를 거리 기준으로 적용
HOLIDAY, BIG_HOLIDAY = 0.15, 0.50             # 설·추석 당일은 절반
FRI_SAT = 1.20
COMP_PER_STORE, COMP_DAYS = 0.97, 90
NOISE_SD = float(sys.argv[2]) if len(sys.argv) > 2 else 0.10
OUT_NAME = sys.argv[3] if len(sys.argv) > 3 else "virtual_sales.csv"

stores = pd.read_csv(out / "demo_stores.csv")
days = pd.date_range(START, END)

w = pd.read_csv("data/raw/weather/daegu_asos143_daily_20230101_20260913.csv", encoding="cp949", parse_dates=["일시"])
w = w.set_index("일시").reindex(days)
rain = w["일강수량(mm)"].fillna(0) >= RAIN_MM                      # 빈칸 = 비 안 옴
heat = w["최고기온(°C)"].ffill() >= HEAT_C
cold = w["최저기온(°C)"] <= COLD_C

hol = pd.read_csv("data/holidays_kr.csv", parse_dates=["date"]).set_index("date")["name"].reindex(days)
big_hol = hol.str.contains("설날|추석", na=False) & ~hol.str.contains("대체", na=False)

# 축제: TourAPI 2026 일정을 2023~2025에도 같은 날짜로 반복
# ponytail: 매년 날짜가 조금씩 다름, 정밀화는 연도별 실제 일정(2024 대구시 파일 등)으로 교체
f = pd.read_csv("data/raw/festival/daegu_festival_tourapi.csv", parse_dates=["start", "end"])
f["x"], f["y"] = Transformer.from_crs("EPSG:4326", "EPSG:5174", always_xy=True).transform(f["lon"].values, f["lat"].values)
fests = pd.concat([f.assign(start=f["start"] + pd.DateOffset(years=k), end=f["end"] + pd.DateOffset(years=k)) for k in (-3, -2, -1, 0)])

# 경쟁점: 500m 안 같은 업종 개업 (select_demo_stores.py와 같은 임시영업 제외 기준)
GROUP = {"통닭(치킨)": "치킨", "호프/통닭": "치킨", "한식": "한식", "식육(숯불구이)": "고깃집", "분식": "분식", "중국식": "중국식"}
lic = pd.read_csv(out / "stores_with_dong.csv", dtype=str)
lic["open"], lic["close"] = pd.to_datetime(lic["open"]), pd.to_datetime(lic["close"])
lic["x"], lic["y"] = pd.to_numeric(lic["좌표정보X(EPSG5174)"]), pd.to_numeric(lic["좌표정보Y(EPSG5174)"])
lic["group"] = lic["업태구분명"].map(GROUP)
temp = lic["소재지전체주소"].str.contains("일원|축제", na=False) | ((lic["close"] - lic["open"]).dt.days < 30)
lic = lic[lic["open"].between(START, END) & ~temp & lic["x"].notna()]

kamis = pd.read_csv("data/raw/kamis/daegu_retail_daily.csv", parse_dates=["date"])
price = kamis.pivot(index="date", columns="item", values="price").reindex(days).ffill().bfill()  # 채소는 평일만 조사

rng = np.random.default_rng(SEED)
t = (days.dayofyear.values - 1) / 365.25
frames = []
for s in stores.itertuples():
    g = s.group
    dist = np.hypot(fests["x"] - s.x, fests["y"] - s.y)
    fest = pd.Series(1.0, index=days)
    for (_, e), dd in zip(fests.iterrows(), dist):
        mult = FEST_1KM if dd <= 1000 else FEST_2KM if dd <= 2000 else 1.0
        span = (days >= e["start"]) & (days <= e["end"])
        fest[span] = np.maximum(fest[span], mult)                   # 겹치면 큰 값 하나만

    comp = lic[(lic["group"] == g) & (np.hypot(lic["x"] - s.x, lic["y"] - s.y) <= 500) & (lic["관리번호"] != s.관리번호)]
    n_comp = sum(((days >= o) & (days < o + pd.Timedelta(days=COMP_DAYS))).astype(int) for o in comp["open"]) if len(comp) else 0
    comp_mult = COMP_PER_STORE ** pd.Series(n_comp, index=days)

    day = (BASE_DAILY[g]
           * (1 + 0.05 * np.cos(2 * np.pi * (t - (PEAK_MONTH[g] - 0.5) / 12)))
           * np.where(days.dayofweek.isin([4, 5]), FRI_SAT, 1.0)
           * np.where(hol.notna(), 1 + HOLIDAY_SIGN[g] * HOLIDAY, 1.0)
           * np.where(big_hol, BIG_HOLIDAY, 1.0)
           * fest.values * comp_mult.values)

    for slot, share in zip(["점심", "저녁", "심야"], SLOT_SHARE[g]):
        base = day * share
        hall = base * (1 - DELIVERY_SHARE[g]) * np.where(rain, RAIN_HALL, 1) * np.where(heat | cold, TEMP_HALL, 1)
        deliv = base * DELIVERY_SHARE[g] * np.where(rain, RAIN_DELIV, 1)
        hall *= rng.lognormal(0, NOISE_SD, len(days))
        deliv *= rng.lognormal(0, NOISE_SD, len(days))
        df = pd.DataFrame({"store_id": s.store_id, "category": g, "adm_nm": s.adm_nm, "date": days.date, "time_slot": slot,
                           "sales_hall": hall.round(-2).astype(int), "sales_delivery": deliv.round(-2).astype(int)})
        df["orders"] = np.maximum(1, ((df["sales_hall"] + df["sales_delivery"]) / TICKET[g]).round()).astype(int)
        cost = 0
        for item, per in USAGE[g].items():
            df[f"qty_{item}"] = (df["orders"] * per).round(2)
            cost = cost + df[f"qty_{item}"] * price[item].values
        df["ingredient_cost"] = np.round(cost, -1).astype(int)
        # 어떤 요인이 걸린 날인지 (검증·조언 근거용)
        df["is_rain"], df["is_heat"], df["is_cold"] = rain.values, heat.values, cold.values
        df["holiday"], df["fest_mult"], df["n_comp_90d"] = hol.values, fest.values, np.asarray(n_comp) * np.ones(len(days), int)
        frames.append(df)

sales = pd.concat(frames, ignore_index=True)
qty_cols = sorted(c for c in sales.columns if c.startswith("qty_"))
sales = sales[[c for c in sales.columns if not c.startswith("qty_")] + qty_cols]
sales[qty_cols] = sales[qty_cols].fillna(0)
sales.to_csv(out / OUT_NAME, index=False, encoding="utf-8-sig")
print(f"{len(sales):,}행 → {out / OUT_NAME}")

# ---------------- 검증 ----------------
assert len(sales) == len(stores) * len(days) * 3
assert sales[["sales_hall", "sales_delivery", "orders", "ingredient_cost"]].ge(0).all().all()
assert not sales.drop(columns="holiday").isna().any().any()

# 생성 규칙이 실제로 들어갔는지: 로그 매출 회귀로 계수 복원 (가게×시간대 고정효과 + 요일·월·휴일 통제)
sales["d"] = pd.to_datetime(sales["date"])
X = pd.get_dummies(sales["store_id"] + sales["time_slot"], drop_first=True, dtype=float)
X = X.join(pd.get_dummies(sales["d"].dt.dayofweek.astype(str) + "dow", drop_first=True, dtype=float))
X = X.join(pd.get_dummies(sales["d"].dt.month.astype(str) + "m", drop_first=True, dtype=float))
X["hol_x_sign"] = sales["holiday"].notna() * sales["category"].map(HOLIDAY_SIGN)
X["big_hol"] = sales["d"].isin(days[big_hol]).astype(float)
X["rain"], X["heat_cold"] = sales["is_rain"].astype(float), (sales["is_heat"] | sales["is_cold"]).astype(float)
X["log_fest"], X["n_comp"] = np.log(sales["fest_mult"]), sales["n_comp_90d"].astype(float)
X["const"] = 1.0
cols = list(X.columns)
expect = {"hall": {"rain": np.log(RAIN_HALL), "heat_cold": np.log(TEMP_HALL), "log_fest": 1.0, "n_comp": np.log(COMP_PER_STORE)},
          "delivery": {"rain": np.log(RAIN_DELIV), "heat_cold": 0.0, "log_fest": 1.0, "n_comp": np.log(COMP_PER_STORE)}}
print("\n회귀로 복원한 계수 (기대값):")
for name, col in [("hall", "sales_hall"), ("delivery", "sales_delivery")]:
    ok = sales[col] > 0
    beta = np.linalg.lstsq(X[ok].to_numpy(), np.log(sales.loc[ok, col]).to_numpy(), rcond=None)[0]
    for k, v in expect[name].items():
        b = beta[cols.index(k)]
        print(f"  {name:8s} {k:9s} {b:+.3f} ({v:+.3f})")
        # 축제일은 표본이 적어(가게당 연 수십 일) 오차 허용을 넓게
        assert abs(b - v) < (0.1 if k == "log_fest" else 0.02), f"{name} {k} 계수 불일치"
print("검증 통과")

# 데모 시나리오 확인
mon = sales.assign(m=sales["d"].dt.to_period("M")).groupby(["store_id", "m"])[["sales_hall", "sales_delivery"]].sum().sum(axis=1)
print("\n가게별 월평균 매출(만원):", (mon.groupby("store_id").mean() / 1e4).round().astype(int).to_dict())
daily = sales.groupby(["store_id", "date"])[["sales_hall", "sales_delivery"]].sum()
print("\nS2 메이커페스타 전후 (9/12~9/13 데이터 끝, 9/19~20은 예보 구간):")
print(daily.loc["S2"].tail(3).to_string())
s3_open = pd.Timestamp("2026-06-24")
s3 = daily.loc["S3"].sum(axis=1)
s3.index = pd.to_datetime(s3.index)
print(f"\nS3 경쟁점 개업(6/24) 전 90일 평균 {s3[s3_open - pd.Timedelta(days=90):s3_open].mean():,.0f}원"
      f" → 후 {s3[s3_open:s3_open + pd.Timedelta(days=80)].mean():,.0f}원")
s1 = daily.loc["S1"].sum(axis=1)
s1.index = pd.to_datetime(s1.index)
print(f"S1 치맥페스티벌(2026-07-01~05) 일평균 {s1['2026-07-01':'2026-07-05'].mean():,.0f}원 vs 7월 나머지 {s1['2026-07-06':'2026-07-31'].mean():,.0f}원")
