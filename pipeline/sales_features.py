"""매출 예측 모델 공통 입력·학습 함수 (validate_sales_model.py, forecast_sales.py 공용)."""
import lightgbm as lgb
import numpy as np
import pandas as pd

# 범주를 고정해야 예측 데이터에 일부 가게·시간대만 있어도 학습 때와 같은 코드로 매핑됨
# (astype("category")는 데이터에 있는 값만으로 범주를 만들어서 가게 1곳만 예측하면 S1로 착각)
# ponytail: 데모 가게 6곳 고정, 실서비스는 가게 ID를 원-핫 대신 가게 특성(업종·동네)으로 바꿀 것
STORE_CATS = sorted(f"S{i}" for i in range(1, 7))
SLOT_CATS = sorted(["점심", "저녁", "심야"])
FEATS = ["store_id", "time_slot", "dow", "month", "doy_sin", "doy_cos", "is_holiday", "big_holiday",
         "is_rain", "is_heat", "is_cold", "fest_mult", "n_comp_90d"]
TARGETS = ["sales_hall", "sales_delivery"]


def features(df):
    holiday = df["holiday"].fillna("")
    X = pd.DataFrame({"store_id": pd.Categorical(df["store_id"], categories=STORE_CATS),
                      "time_slot": pd.Categorical(df["time_slot"], categories=SLOT_CATS),
                      "dow": df["date"].dt.dayofweek, "month": df["date"].dt.month,
                      "doy_sin": np.sin(2 * np.pi * df["date"].dt.dayofyear / 365.25),
                      "doy_cos": np.cos(2 * np.pi * df["date"].dt.dayofyear / 365.25),
                      "is_holiday": df["holiday"].notna().astype(int),
                      "big_holiday": (holiday.str.contains("설날|추석") & ~holiday.str.contains("대체")).astype(int),
                      "is_rain": df["is_rain"].astype(int), "is_heat": df["is_heat"].astype(int), "is_cold": df["is_cold"].astype(int),
                      "fest_mult": df["fest_mult"], "n_comp_90d": df["n_comp_90d"]}, index=df.index)
    return X[FEATS]


def _offset(df, table):
    return pd.Series(list(zip(df["store_id"], df["time_slot"])), index=df.index).map(table).to_numpy()


def fit(train, target):
    """가게×시간대 평소 수준(log 평균)을 init_score로 깔고, 트리는 날씨·축제·휴일에 따른 '변화'만 학습.
    offset 없이 학습하면 드문 축제일에 트리가 가게 구분 없이 평균을 내서 가게별로 ±15% 치우침
    (2026-06-29 재현 검증에서 S1 치맥페스티벌 주간 과소예측으로 발견)."""
    y = np.log1p(train[target])
    table = y.groupby([train["store_id"], train["time_slot"]]).mean().to_dict()
    params = dict(n_estimators=600, learning_rate=0.03, num_leaves=31, min_child_samples=10, subsample=0.8, subsample_freq=1,
                  colsample_bytree=0.9, random_state=42, verbose=-1)
    model = lgb.LGBMRegressor(**params).fit(features(train), y, init_score=_offset(train, table))
    model.offset_table_ = table
    return model


def predict(models, df):
    # sklearn predict()는 init_score를 더하지 않으므로 offset을 직접 더함
    return pd.DataFrame({t: np.expm1(m.predict(features(df)) + _offset(df, m.offset_table_)) for t, m in models.items()}, index=df.index)


def wmape(y, yhat):
    return float(np.abs(y - yhat).sum() / y.sum())


def daily_total(df, cols):
    return df.groupby(["store_id", "date"])[cols].sum().sum(axis=1)
