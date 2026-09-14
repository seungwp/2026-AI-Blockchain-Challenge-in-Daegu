import requests
import pandas as pd
import os

url = 'http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst'
api_key = '키_삭제됨'  # ← 발급받은 키를 여기에 넣으세요

params = {
    'ServiceKey': requests.utils.unquote(api_key), 
    'pageNo': '1',
    'numOfRows': '1000',
    'dataType': 'JSON',
    'base_date': '20260914', 
    'base_time': '0500',     
    'nx': '89',              
    'ny': '90'               
}

print("데이터를 요청하는 중입니다...")
response = requests.get(url, params=params)
data = response.json()

# 💡 서버가 보낸 원본 데이터를 먼저 출력해봅니다
print("\n[API 응답 결과]")
print(data)
print("-" * 50)

# 정상적으로 'response'가 있는지 확인하고 처리
if 'response' in data and data['response']['header']['resultCode'] == '00':
    items = data['response']['body']['items']['item']
    df = pd.DataFrame(items)
    
    # 폴더가 없으면 에러가 나므로 미리 만들어줍니다
    os.makedirs('data/raw/forecast', exist_ok=True)
    
    df.to_csv('data/raw/forecast/daegu_weather_forecast.csv', index=False, encoding='utf-8-sig')
    print("✅ 날씨 데이터가 'daegu_weather_forecast.csv'로 성공적으로 저장되었습니다!")
else:
    print("🚨 날씨 데이터를 가져오지 못했습니다. 위 [API 응답 결과]의 에러 메시지를 확인해주세요.")