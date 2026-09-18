"""실행 중인 백엔드의 실제 공공데이터 연결 확인. 저장소 루트에서 실행."""
import json
import os
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE = os.environ.get('GOLMOK_BASE', 'http://localhost:9091')


def call(path, payload=None):
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode('utf-8')
    req = Request(BASE + path, data=body, headers={'Content-Type': 'application/json'})
    with urlopen(req, timeout=90) as response:
        return json.load(response)


assert call('/api/health')['status'] == 'ok'
with urlopen(BASE + '/swagger-ui.html') as response:
    assert response.status == 200
addresses = call('/api/addresses/search?' + urlencode({'keyword': '대구광역시 중구 공평로 88'}))
assert addresses and addresses[0]['district'] == '중구'
print('실제 도로명주소 검색 / Swagger 정상', flush=True)
stores = call('/api/stores/search?' + urlencode({'keyword': '치킨'}))
assert stores
report = call('/api/reports', {'storeId': stores[0]['id'], 'mainMenu': '후라이드 치킨', 'menuCategory': '치킨'})
price = report['ingredientPrices'][0]
assert len(price['history']) >= 20
assert price['vsPreviousWeekRatio'] is not None
assert price['predictionStale'] and price['probSpike'] is None and not price['alert']
assert call('/api/reports/' + str(report['reportId'])) == report
assert all(source in {s['id'] for s in report['sources']} for source in report['commercialArea']['sourceIds'])
out = Path(__file__).resolve().parents[2] / 'tmp/public-data-report.json'
out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'reportId': report['reportId'], 'storeId': stores[0]['id'], 'priceDate': price['priceDate'],
                  'historyDays': len(price['history']), 'comparisonDate': price['comparisonDate'],
                  'predictionDate': price['predictionDate'], 'areaSources': report['commercialArea']['sourceIds'],
                  'festivals': len(report['festivals'])}, ensure_ascii=False), flush=True)
manual = call('/api/stores/manual', {'address': '대구광역시 중구 공평로 88'})
assert manual['latitude'] is not None and manual['longitude'] is not None
manual_report = call('/api/reports', {'storeId': manual['id'], 'mainMenu': '국밥', 'menuCategory': '국물요리'})
assert manual_report['commercialArea'] is not None
print('네이버 지오코딩 직접 입력 주소: 좌표·상권 분석 확인', flush=True)
print('실제 연동 검증 통과', flush=True)
