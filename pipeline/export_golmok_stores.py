"""
골목 한 주 백엔드 시드용 대구 음식점 목록을 만든다.

입력: data/raw/licenses/식품_일반음식점_대구광역시.csv (식품의약품안전처 인허가, cp949)
출력: golmok-one-week/backend/src/main/resources/data/daegu_stores.csv

영업 중인 업소만 남기고, 좌표를 EPSG:5174 -> WGS84 로 변환한다.
관리번호는 내보내지 않는다(CLAUDE.md '실존 가게 표시 범위').

실행: py -X utf8 pipeline/export_golmok_stores.py
"""
import csv
import io
import sys
from pathlib import Path

from pyproj import Transformer

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data/raw/licenses/식품_일반음식점_대구광역시.csv"
OUT = ROOT / "golmok-one-week/backend/src/main/resources/data/daegu_stores.csv"

# 음식점 운영 가이드와 무관한 업태는 제외
SKIP_TYPES = {"기타", "출장조리", "이동조리", "일반조리판매", "푸드트럭"}

to_wgs84 = Transformer.from_crs("EPSG:5174", "EPSG:4326", always_xy=True)


def district_of(address: str) -> str:
    """'대구광역시 중구 동성로4길 20-7' -> '중구'."""
    parts = address.split()
    return parts[1] if len(parts) > 1 else ""


def main() -> int:
    rows, skipped_closed, skipped_type, skipped_coord = [], 0, 0, 0

    with io.open(SRC, encoding="cp949", newline="") as f:
        for r in csv.DictReader(f):
            if not (r.get("영업상태명") or "").startswith("영업"):
                skipped_closed += 1
                continue

            kind = (r.get("업태구분명") or "").strip()
            if kind in SKIP_TYPES or not kind:
                skipped_type += 1
                continue

            name = (r.get("사업장명") or "").strip()
            road = (r.get("도로명주소") or "").strip()
            lot = (r.get("소재지전체주소") or r.get("지번주소") or "").strip()
            address = lot or road
            if not name or not address:
                skipped_coord += 1
                continue

            try:
                x = float(r.get("좌표정보(X)") or "")
                y = float(r.get("좌표정보(Y)") or "")
                lon, lat = to_wgs84.transform(x, y)
            except (TypeError, ValueError):
                skipped_coord += 1
                continue

            # 대구 밖 좌표(잘못된 값) 방어
            if not (35.5 < lat < 36.3 and 128.2 < lon < 129.0):
                skipped_coord += 1
                continue

            rows.append([name, kind, address, road, district_of(address),
                         "%.6f" % lat, "%.6f" % lon])

    rows.sort(key=lambda r: (r[4], r[0]))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["name", "category", "address", "roadAddress", "district", "latitude", "longitude"])
        w.writerows(rows)

    print("영업중 음식점 %d곳 -> %s (%.1f MB)"
          % (len(rows), OUT.relative_to(ROOT), OUT.stat().st_size / 1024 / 1024))
    print("  제외: 폐업·휴업 %d, 대상외 업태 %d, 좌표·필수값 이상 %d"
          % (skipped_closed, skipped_type, skipped_coord))

    from collections import Counter
    print("  업태 상위:", Counter(r[1] for r in rows).most_common(6))
    print("  구·군 상위:", Counter(r[4] for r in rows).most_common(5))
    return 0


if __name__ == "__main__":
    sys.exit(main())
