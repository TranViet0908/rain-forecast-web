import requests
import pandas as pd
import time
import os

# --- CẤU HÌNH ---
START_YEAR = 1984
END_YEAR = 2024
OUTPUT_FILE = 'weather_data_1984_2024.csv'

# Danh sách 11 địa điểm khớp với Database
LOCATIONS = [
    {"name": "Thanh Hoa", "lat": 19.807, "lon": 105.776},
    {"name": "Nghe An", "lat": 18.673, "lon": 105.676},
    {"name": "Ha Tinh", "lat": 18.337, "lon": 105.903},
    {"name": "Quang Tri Gop", "lat": 16.820, "lon": 107.098},
    {"name": "Hue", "lat": 16.463, "lon": 107.590},
    {"name": "Da Nang Gop", "lat": 16.054, "lon": 108.202},
    {"name": "Quang Ngai Gop", "lat": 15.120, "lon": 108.792},
    {"name": "Gia Lai Gop", "lat": 13.782, "lon": 109.219},
    {"name": "Dak Lak Gop", "lat": 13.088, "lon": 109.311},
    {"name": "Khanh Hoa Gop", "lat": 12.238, "lon": 109.196},
    {"name": "Lam Dong Gop", "lat": 10.928, "lon": 108.102}
]

BASE_URL = "https://power.larc.nasa.gov/api/temporal/daily/point"

def fetch_data():
    all_data = []
    print(f"--- BAT DAU TAI DU LIEU TU {START_YEAR} DEN {END_YEAR} ---")

    for loc in LOCATIONS:
        print(f"\nDang tai: {loc['name']}...")

        params = {
            "parameters": "T2M,RH2M,WS2M,TS,PRECTOTCORR",
            "community": "AG",
            "longitude": loc['lon'],
            "latitude": loc['lat'],
            "start": f"{START_YEAR}0101",
            "end": f"{END_YEAR}1231",
            "format": "JSON"
        }

        try:
            response = requests.get(BASE_URL, params=params, timeout=45)
            if response.status_code != 200:
                print(f"Loi HTTP: {response.status_code}")
                continue

            data = response.json()['properties']['parameter']

            # Parsing dữ liệu
            count = 0
            for date_str, temp in data['T2M'].items():
                if temp == -999: continue

                year = int(date_str[:4])
                period = 1 if year <= 1999 else (2 if year <= 2014 else 3)

                row = {
                    "location_name": loc['name'],
                    "date": date_str,  # <--- DÒNG NÀY QUAN TRỌNG NHẤT
                    "lst": data['TS'].get(date_str, -999),
                    "humidity": data['RH2M'].get(date_str, -999),
                    "temperature": temp,
                    "wind_speed": data['WS2M'].get(date_str, -999),
                    "rainfall": data['PRECTOTCORR'].get(date_str, -999),
                    "period": period
                }

                if -999 not in row.values():
                    all_data.append(row)
                    count += 1

            print(f"--> Xong {loc['name']}. Lay duoc {count} dong.")
            time.sleep(1)

        except Exception as e:
            print(f"Loi tai {loc['name']}: {e}")

    if all_data:
        # Kiểm tra kỹ xem dữ liệu có cột date không trước khi lưu
        if 'date' in all_data[0]:
            pd.DataFrame(all_data).to_csv(OUTPUT_FILE, index=False)
            print(f"\n--> THANH CONG! File {OUTPUT_FILE} da co cot 'date'.")
        else:
            print("LOI: Du lieu van thieu cot date!")
    else:
        print("THAT BAI: Khong tai duoc du lieu.")

if __name__ == "__main__":
    fetch_data()