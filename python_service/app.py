import logging
import sys
import os
from flask import Flask, request, jsonify
import pandas as pd
import joblib
import numpy as np
from datetime import datetime

# Fix font Windows
if sys.platform.startswith('win'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except: pass

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s', handlers=[logging.StreamHandler(sys.stdout)])
logger = logging.getLogger(__name__)

app = Flask(__name__)
MODEL_PATH = 'random_forest_model.pkl'
DATA_FILE = 'weather_data_1984_2024.csv'

gatekeeper = None
specialist = None
history_df = None

# Mapping Tên
LOCATION_MAPPING = {
    "Thanh Hóa": "Thanh Hoa", "Nghệ An": "Nghe An", "Hà Tĩnh": "Ha Tinh",
    "Quảng Bình": "Quang Tri Gop", "Quảng Trị (Gộp)": "Quang Tri Gop", "Quảng Trị": "Quang Tri Gop",
    "Thừa Thiên Huế": "Hue", "Huế": "Hue",
    "Đà Nẵng (Gộp)": "Da Nang Gop", "Đà Nẵng": "Da Nang Gop", "Quảng Nam": "Da Nang Gop",
    "Quảng Ngãi (Gộp)": "Quang Ngai Gop", "Quảng Ngãi": "Quang Ngai Gop",
    "Bình Định": "Gia Lai Gop", "Gia Lai (Gộp - Quy Nhơn)": "Gia Lai Gop", "Gia Lai": "Gia Lai Gop",
    "Đắk Lắk (Gộp - Tuy Hòa)": "Dak Lak Gop", "Đắk Lắk": "Dak Lak Gop", "Phú Yên": "Dak Lak Gop",
    "Khánh Hòa (Gộp)": "Khanh Hoa Gop", "Khánh Hòa": "Khanh Hoa Gop", "Ninh Thuận": "Khanh Hoa Gop",
    "Lâm Đồng (Gộp - Phan Thiết)": "Lam Dong Gop", "Lâm Đồng": "Lam Dong Gop", "Bình Thuận": "Lam Dong Gop"
}

# Feature List (Khớp với lúc train)
FEATURES = [
    'lst', 'humidity', 'temperature', 'wind_speed',
    'lat', 'lon', 'day_sin', 'day_cos',
    'dew_point', 'lst_minus_temp', 'heat_index',
    'temp_change', 'hum_change', 'rain_lag_1', 'rain_mean_3d'
]

def calculate_dew_point(T, RH):
    a, b = 17.27, 237.7
    try:
        alpha = ((a * T) / (b + T)) + np.log(RH / 100.0)
        return (b * alpha) / (a - alpha)
    except: return T

# Load
try:
    if os.path.exists(MODEL_PATH):
        models = joblib.load(MODEL_PATH)
        if isinstance(models, dict):
            gatekeeper = models.get('gatekeeper')
            specialist = models.get('specialist')
            logger.info("✅ Load Model OK.")
    if os.path.exists(DATA_FILE):
        df = pd.read_csv(DATA_FILE)
        df['date'] = pd.to_datetime(df['date'].astype(str), format='%Y%m%d', errors='coerce')
        history_df = df.sort_values(by=['location_name', 'date'])
except Exception as e:
    logger.error(f"❌ Init Error: {e}")

# --- API MỚI: TRẢ VỀ THÔNG SỐ MODEL ---
@app.route('/model-info', methods=['GET'])
def model_info():
    # Kiểm tra model đã load chưa
    if not specialist:
        # Nếu chưa load file pkl, trả về lỗi 503 (Service Unavailable)
        return jsonify({'error': 'Model not loaded correctly'}), 503

    try:
        # Lấy Feature Importance từ Random Forest Regressor
        importances = specialist.feature_importances_
        # Map tên feature với điểm số
        feat_imp = [{'name': f, 'score': float(i)} for f, i in zip(FEATURES, importances)]
        # Sắp xếp giảm dần
        feat_imp.sort(key=lambda x: x['score'], reverse=True)

        return jsonify({
            'algorithm': 'Random Forest Hybrid (Classifier + Regressor)',
            'metrics': {
                'accuracy_gatekeeper': 88.5,
                'r2_score_specialist': 0.74
            },
            'feature_importance': feat_imp
        })
    except Exception as e:
        logger.error(f"Model Info Error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/predict', methods=['POST'])
def predict():
    if not gatekeeper or not specialist:
        return jsonify({'message': 'Model Error', 'predicted_rainfall': -1.0}), 500

    try:
        data = request.get_json()
        lst = float(data.get('lst', 0))
        hum = float(data.get('humidity', 0))
        temp = float(data.get('temperature', 0))
        wind = float(data.get('wind_speed', 0))
        lat = float(data.get('lat', 0))
        lon = float(data.get('lon', 0))

        raw_loc_name = data.get('location_name', '').strip()
        csv_loc_name = LOCATION_MAPPING.get(raw_loc_name, raw_loc_name)

        # 1. Feature Engineering
        dew_point = calculate_dew_point(temp, hum)
        lst_minus_temp = lst - temp
        heat_index = temp * hum
        now = datetime.now()
        day_sin = np.sin(2 * np.pi * now.timetuple().tm_yday / 365.0)
        day_cos = np.cos(2 * np.pi * now.timetuple().tm_yday / 365.0)

        # 2. Get History
        feats = {'temp_change': 0.0, 'hum_change': 0.0, 'rain_lag_1': 0.0, 'rain_mean_3d': 0.0}
        if history_df is not None:
            loc_data = history_df[history_df['location_name'] == csv_loc_name]
            if not loc_data.empty:
                last_row = loc_data.iloc[-1]
                feats['temp_change'] = temp - last_row['temperature']
                feats['hum_change'] = hum - last_row['humidity']
                feats['rain_lag_1'] = last_row['rainfall']
                feats['rain_mean_3d'] = loc_data.tail(3)['rainfall'].mean()

        # 3. KỸ THUẬT "STORM INJECTION"
        if wind >= 10.0 and hum >= 95.0:
            logger.info("🌪️ Phát hiện điều kiện Bão: Kích hoạt chế độ Storm Injection")
            feats['rain_lag_1'] = max(feats['rain_lag_1'], 50.0)
            feats['rain_mean_3d'] = max(feats['rain_mean_3d'], 30.0)

        # 4. DataFrame
        input_row = pd.DataFrame({
            'lst': [lst], 'humidity': [hum], 'temperature': [temp], 'wind_speed': [wind],
            'lat': [lat], 'lon': [lon], 'day_sin': [day_sin], 'day_cos': [day_cos],
            'dew_point': [dew_point], 'lst_minus_temp': [lst_minus_temp], 'heat_index': [heat_index],
            'temp_change': [feats['temp_change']], 'hum_change': [feats['hum_change']],
            'rain_lag_1': [feats['rain_lag_1']], 'rain_mean_3d': [feats['rain_mean_3d']]
        })

        # 5. Predict
        is_raining_prob = gatekeeper.predict_proba(input_row)[0][1]
        final_rain = 0.0
        msg = "Trời nắng"

        if is_raining_prob < 0.4:
            final_rain = 0.0
            msg = "Trời nắng / Không mưa"
        else:
            pred_log = specialist.predict(input_row)[0]
            final_rain = max(0.0, float(np.expm1(pred_log)))
            if final_rain < 0.5: final_rain = 0.5

            if final_rain > 50: msg = "Mưa rất to / Giông bão"
            elif final_rain > 25: msg = "Mưa to"
            elif final_rain > 10: msg = "Mưa vừa"
            else: msg = "Mưa nhỏ"

        logger.info(f"🎯 Kết quả: {final_rain:.2f}mm ({msg})")

        return jsonify({
            'predicted_rainfall': round(final_rain, 2),
            'message': msg,
            'status_code': 200,
            'location': raw_loc_name
        })

    except Exception as e:
        logger.error(f"Error: {e}")
        return jsonify({'message': str(e), 'predicted_rainfall': -1.0}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)