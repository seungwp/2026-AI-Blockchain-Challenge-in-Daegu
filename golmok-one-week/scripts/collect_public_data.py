"""공공 API 수집. 저장소 루트 .env 사용, 키/요청 URL/원문 오류는 출력하지 않는다."""
import argparse
import json
import os
import csv
import re
import html
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlencode, unquote
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'golmok-one-week/backend/src/main/resources/data'
TODAY = datetime.now(ZoneInfo('Asia/Seoul')).date()
KAMIS_CODES = {
    '배추': ('200', '211', '', '04'), '무': ('200', '231', '', '04'),
    '양파': ('200', '245', '00', '04'), '대파': ('200', '246', '00', '04'),
    '깐마늘': ('200', '258', '01', '04'), '삼겹살': ('500', '4304', '27', '00'),
    '닭': ('500', '9901', '99', '00'), '계란': ('500', '9903', '23', '71'),
}


def save(name, data):
    target = DATA / name
    temp = target.with_suffix('.tmp')
    temp.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    temp.replace(target)


def normalize_name(value):
    return re.sub(r'[^가-힣a-z0-9]', '', value.lower())


def road(value):
    # 층/호가 다른 동명 점포는 아래의 유일 후보 검사에서 제외한다.
    return re.split(r'[,（(]', value)[0].strip()


def collect_shops(keys):
    def page(n):
        data = get('https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong',
                   dict(serviceKey=unquote(keys.get('SBIZ_API_KEY') or keys['DATA_GO_KR_KEY']),
                        divId='ctprvnCd', key='27', indsLclsCd='I2', numOfRows=1000, pageNo=n, type='json'))
        if data.get('header', {}).get('resultCode') != '00':
            raise RuntimeError('상가정보 응답 코드 오류')
        return data
    first = page(1)
    total = int(first['body']['totalCount'])
    rows = list(first['body']['items'])
    months = {first['header']['stdrYm']}
    with ThreadPoolExecutor(max_workers=3) as pool:
        for data in pool.map(page, range(2, (total + 999) // 1000 + 1)):
            rows.extend(data['body']['items'])
            months.add(data['header']['stdrYm'])
    if len(rows) != total or len(months) != 1:
        raise RuntimeError('상가정보 페이지 누락/기준월 불일치')
    index = {}
    for r in rows:
        if r['ctprvnCd'] != '27' or r['indsLclsCd'] != 'I2':
            raise RuntimeError('지역/업종 필터 불일치')
        for name in {r['bizesNm'], r['bizesNm'] + r.get('brchNm', '')}:
            key = (normalize_name(name), road(r['rdnmAdr']))
            index.setdefault(key, {})[r['bizesId']] = r
    with (DATA / 'daegu_stores.csv').open(encoding='utf-8-sig', newline='') as f:
        stores = list(csv.DictReader(f))
    matches = []
    source_use = {}
    for s in stores:
        candidates = index.get((normalize_name(s['name']), road(s['roadAddress'])), {})
        if len(candidates) != 1:
            continue
        r = next(iter(candidates.values()))
        try:
            dy = (float(s['latitude']) - float(r['lat'])) * 111000
            dx = (float(s['longitude']) - float(r['lon'])) * 88800
            if dx * dx + dy * dy > 100 ** 2:
                continue
        except (ValueError, TypeError):
            continue
        source_use[r['bizesId']] = source_use.get(r['bizesId'], 0) + 1
        matches.append(dict(name=s['name'], roadAddress=s['roadAddress'], address=s['address'],
                            detailCode=r['indsSclsCd'], detailName=r['indsSclsNm'],
                            sourceId=r['bizesId'], latitude=r['lat'], longitude=r['lon']))
    matches = [r for r in matches if source_use[r['sourceId']] == 1]
    # 업소 관리번호는 저장/화면에 전달하지 않는다.
    for r in matches:
        del r['sourceId']
    save('store_enrichment.json', dict(fetchedAt=str(TODAY), asOf=next(iter(months)),
         fetchedCount=total, matchedCount=len(matches), items=matches))
    print(f'상가정보 {total}건 수집, 유일 일치 {len(matches)}곳 보강', flush=True)


def tour_body(keys, endpoint, **params):
    data = get('https://apis.data.go.kr/B551011/KorService2/' + endpoint,
               dict(serviceKey=unquote(keys['DATA_GO_KR_KEY']), MobileOS='ETC', MobileApp='golmok',
                    _type='json', **params))['response']
    if data['header']['resultCode'] != '0000':
        raise RuntimeError('TourAPI 응답 코드 오류')
    return data['body']


def clean(value):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]*>', ' ', html.unescape(value or ''))).strip()


def collect_festivals(keys):
    rows, n = [], 1
    while True:
        body = tour_body(keys, 'searchFestival2', numOfRows=100, pageNo=n,
                         eventStartDate=f'{TODAY.year}0101', lDongRegnCd='27')
        rows.extend(body['items']['item'] if body['items'] else [])
        if len(rows) >= int(body['totalCount']):
            break
        n += 1
        if n > 100:
            raise RuntimeError('TourAPI 페이지 누락')
    out = []
    for r in rows:
        try:
            detail = tour_body(keys, 'detailIntro2', contentId=r['contentid'], contentTypeId=15)
            d = detail['items']['item'][0] if detail['items'] else {}
        except Exception:
            # 목록은 사용하되 상세 실패 여부를 명시한다.
            d = {}
        out.append(dict(contentId=r['contentid'], name=r['title'],
                        startDate=datetime.strptime(r['eventstartdate'], '%Y%m%d').date().isoformat(),
                        endDate=datetime.strptime(r['eventenddate'], '%Y%m%d').date().isoformat(),
                        address=r['addr1'], latitude=float(r['mapy']) if r['mapy'] else None,
                        longitude=float(r['mapx']) if r['mapx'] else None,
                        locationName=clean(d.get('eventplace')) or r['addr1'],
                        playTime=clean(d.get('playtime')), fee=clean(d.get('usetimefestival')),
                        contact=clean(r.get('tel')), detailAvailable=bool(d)))
    if not out:
        raise RuntimeError('행사 응답 비어 있음: 기존 파일 유지')
    save('festival_details.json', dict(fetchedAt=str(TODAY), items=out))
    print(f'행사 {len(out)}건 갱신, 상세 {sum(r["detailAvailable"] for r in out)}건', flush=True)


def kamis(keys, codes):
    return get('https://www.kamis.or.kr/service/price/xml.do', dict(
        action='periodRetailProductList', p_cert_key=keys['KAMIS_CERT_KEY'], p_cert_id=keys['KAMIS_CERT_ID'],
        p_returntype='json', p_startday=str(TODAY - timedelta(days=30)), p_endday=str(TODAY),
        p_itemcategorycode=codes[0], p_itemcode=codes[1], p_kindcode=codes[2],
        p_productrankcode=codes[3], p_countrycode='2200', p_convert_kg_yn='N'))


def collect_prices(keys):
    out = []
    for name, codes in KAMIS_CODES.items():
        data = kamis(keys, codes)
        records = data.get('data', {}).get('item', [])
        if not isinstance(records, list):
            raise RuntimeError(f'{name} 가격 응답 오류')
        points, normals = {}, {}
        for r in records:
            if r.get('countyname') not in ('평균', '평년'):
                continue
            try:
                date = datetime.strptime(str(r['yyyy']) + '-' + r['regday'], '%Y-%m/%d').date().isoformat()
                price = float(str(r['price']).replace(',', ''))
                if price <= 0:
                    continue
            except (KeyError, ValueError):
                continue
            (points if r['countyname'] == '평균' else normals)[date] = price
        if not points:
            raise RuntimeError(f'{name} 유효 가격 없음: 기존 파일 유지')
        history = [dict(date=d, price=p) for d, p in sorted(points.items())]
        latest = history[-1]
        out.append(dict(item=name, region='대구', market='소매', priceDate=latest['date'],
                        price=latest['price'], normalPrice=normals.get(latest['date']), history=history))
        print(f'{name}: 가격 이력 {len(history)}일, 기준일 {latest["date"]}', flush=True)
    save('ingredient_history.json', dict(fetchedAt=str(TODAY), items=out))


def env():
    values = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.strip().startswith('#'):
            k, v = line.split('=', 1)
            values[k.strip()] = v.strip().strip('\"').strip("'")
    return values | dict(os.environ)


def get(url, params):
    try:
        with urlopen(url + '?' + urlencode(params), timeout=30) as response:
            raw = response.read().decode('utf-8-sig')
        return json.loads(raw)
    except Exception as exc:
        # HTTP/URL 예외 문자열은 인증키가 포함된 URL을 담을 수 있다.
        raise RuntimeError(type(exc).__name__) from None


def safe_output(data):
    """일부 API가 응답 condition에 인증정보를 되돌려주므로 응답도 필터링한다."""
    if isinstance(data, dict):
        return {k: safe_output(v) for k, v in data.items()
                if k.lower() not in {'condition', 'p_key', 'p_id', 'confmkey', 'servicekey', 'p_cert_key', 'p_cert_id'}}
    if isinstance(data, list):
        return [safe_output(v) for v in data]
    return data


def probe(keys):
    calls = {
        'sbiz': ('https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong',
                 dict(serviceKey=unquote(keys.get('SBIZ_API_KEY') or keys['DATA_GO_KR_KEY']),
                      divId='ctprvnCd', key='27', indsLclsCd='I2', numOfRows=1, pageNo=1, type='json')),
        'tour': ('https://apis.data.go.kr/B551011/KorService2/searchFestival2',
                 dict(serviceKey=unquote(keys['DATA_GO_KR_KEY']), MobileOS='ETC', MobileApp='golmok',
                      _type='json', numOfRows=1, pageNo=1, eventStartDate='20260101', lDongRegnCd='27')),
    }
    for name, (url, params) in calls.items():
        try:
            data = get(url, params)
            # 응답만 출력; 요청/키는 출력하지 않는다.
            print(name, json.dumps(safe_output(data), ensure_ascii=False)[:6500], flush=True)
        except Exception as exc:
            print(name, type(exc).__name__, str(exc), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--probe', action='store_true')
    parser.add_argument('--collect', choices=['all', 'shops', 'festivals', 'prices'])
    parser.add_argument('--probe-prices', action='store_true')
    args = parser.parse_args()
    if args.probe:
        probe(env())
    if args.probe_prices:
        try:
            print(json.dumps(safe_output(kamis(env(), KAMIS_CODES['닭'])), ensure_ascii=False)[:8000])
        except Exception as exc:
            print(type(exc).__name__, str(exc))
    if args.collect:
        keys = env()
        failed = False
        for name, fn in [('shops', collect_shops), ('festivals', collect_festivals), ('prices', collect_prices)]:
            if args.collect in ('all', name):
                try:
                    fn(keys)
                except Exception as exc:
                    failed = True
                    print(name, '실패: 기존 파일 유지', str(exc), flush=True)
        raise SystemExit(1 if failed else 0)
