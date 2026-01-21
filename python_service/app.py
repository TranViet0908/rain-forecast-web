import logging
import sys
import os
import joblib
import numpy as np
import pandas as pd
from flask import Flask, request, jsonify
from datetime import datetime

# Import Keras để load LSTM
from tensorflow.keras.models import load_model

# Setup Logging
if sys.platform.startswith('win'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except: pass

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s', handlers=[logging.StreamHandler(sys.stdout)])
logger = logging.getLogger(__name__)

app = Flask(__name__)

# ĐƯỜNG DẪN FILE
MODEL_DIR = 'models'
RF_PATH = os.path.join(MODEL_DIR, 'rf_gatekeeper.pkl')
LSTM_PATH = os.path.join(MODEL_DIR, 'lstm_specialist.h5')
SCALER_PATH = os.path.join(MODEL_DIR, 'scaler.pkl')
DATA_FILE = 'weather_data_1984_2024.csv'

# Biến toàn cục chứa Models
rf_gatekeeper = None
lstm_specialist = None
scaler = None
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

# Feature List (Phải đúng thứ tự lúc train)
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

# --- LOAD RESOURCES ---
try:
    logger.info("⏳ Loading Models...")
    if os.path.exists(RF_PATH):
        rf_gatekeeper = joblib.load(RF_PATH)
        logger.info("✅ RF Gatekeeper Loaded.")

    if os.path.exists(SCALER_PATH):
        scaler = joblib.load(SCALER_PATH)
        logger.info("✅ Scaler Loaded.")

    if os.path.exists(LSTM_PATH):
        # Thêm compile=False để bỏ qua lỗi metric 'mse'
        lstm_specialist = load_model(LSTM_PATH, compile=False)
        logger.info("✅ LSTM Specialist Loaded.")

    if os.path.exists(DATA_FILE):
        df = pd.read_csv(DATA_FILE)
        df['date'] = pd.to_datetime(df['date'].astype(str), format='%Y%m%d', errors='coerce')
        history_df = df.sort_values(by=['location_name', 'date'])
        logger.info("✅ History Data Loaded.")
except Exception as e:
    logger.error(f"❌ Init Error: {e}")

@app.route('/model-info', methods=['GET'])
def model_info():
    if not rf_gatekeeper or not lstm_specialist:
        return jsonify({'error': 'Model not fully loaded'}), 503

    # Lấy Feature Importance từ RF (LSTM không có feature importance trực tiếp)
    importances = rf_gatekeeper.feature_importances_
    feat_imp = [{'name': f, 'score': float(i)} for f, i in zip(FEATURES, importances)]
    feat_imp.sort(key=lambda x: x['score'], reverse=True)

    return jsonify({
        'algorithm': 'Hybrid Model: Random Forest (Classification) + LSTM (Regression)',
        'metrics': {
            'gatekeeper_accuracy': '~88% (RF)',
            'specialist_r2': '~0.81 (LSTM)'
        },
        'feature_importance': feat_imp
    })

@app.route('/predict', methods=['POST'])
def predict():
    if not rf_gatekeeper or not lstm_specialist or not scaler:
        return jsonify({'message': 'System initializing...', 'predicted_rainfall': -1.0}), 503

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

        # 1. Feature Engineering (Tính toán đặc trưng vật lý)
        dew_point = calculate_dew_point(temp, hum)
        lst_minus_temp = lst - temp
        heat_index = temp * hum
        now = datetime.now()
        day_sin = np.sin(2 * np.pi * now.timetuple().tm_yday / 365.0)
        day_cos = np.cos(2 * np.pi * now.timetuple().tm_yday / 365.0)

        # 2. Lấy dữ liệu lịch sử (Lag Features)
        feats = {'temp_change': 0.0, 'hum_change': 0.0, 'rain_lag_1': 0.0, 'rain_mean_3d': 0.0}
        if history_df is not None:
            loc_data = history_df[history_df['location_name'] == csv_loc_name]
            if not loc_data.empty:
                last_row = loc_data.iloc[-1]
                feats['temp_change'] = temp - last_row['temperature']
                feats['hum_change'] = hum - last_row['humidity']
                feats['rain_lag_1'] = last_row['rainfall']
                feats['rain_mean_3d'] = loc_data.tail(3)['rainfall'].mean()

        # 3. --- REALISM ALGORITHM: STORM INJECTION ---
        # Logic này giúp mô hình "thông minh" hơn với các tình huống cực đoan
        is_storm_condition = False
        if wind >= 10.0 and hum >= 93.0:
            logger.info("🌪️ Storm Injection Activated: Điều kiện bão/áp thấp nhiệt đới")
            is_storm_condition = True
            # Giả lập lịch sử mưa lớn để kích thích LSTM dự báo cao lên
            feats['rain_lag_1'] = max(feats['rain_lag_1'], 50.0)
            feats['rain_mean_3d'] = max(feats['rain_mean_3d'], 30.0)

        # 4. Tạo DataFrame đầu vào
        input_row = pd.DataFrame({
            'lst': [lst], 'humidity': [hum], 'temperature': [temp], 'wind_speed': [wind],
            'lat': [lat], 'lon': [lon], 'day_sin': [day_sin], 'day_cos': [day_cos],
            'dew_point': [dew_point], 'lst_minus_temp': [lst_minus_temp], 'heat_index': [heat_index],
            'temp_change': [feats['temp_change']], 'hum_change': [feats['hum_change']],
            'rain_lag_1': [feats['rain_lag_1']], 'rain_mean_3d': [feats['rain_mean_3d']]
        })

        # Đảm bảo thứ tự cột đúng như lúc train
        input_row = input_row[FEATURES]

        # 5. PREDICT FLOW
        # BƯỚC A: RF Gatekeeper (Có mưa hay không?)
        # Lấy xác suất mưa (class 1)
        rain_prob = rf_gatekeeper.predict_proba(input_row)[0][1]

        final_rain = 0.0
        msg = "Trời nắng"

        # Ngưỡng quyết định mưa: Nếu Storm Injection bật, giảm ngưỡng xuống 30% để bắt nhạy hơn
        threshold = 0.3 if is_storm_condition else 0.45

        if rain_prob < threshold:
            final_rain = 0.0
            msg = "Trời nắng / Không mưa"
        else:
            # BƯỚC B: LSTM Specialist (Mưa bao nhiêu?)
            # b1. Chuẩn hóa dữ liệu (Scaling)
            input_scaled = scaler.transform(input_row)

            # b2. Reshape sang 3D [1, 1, n_features] cho LSTM
            input_lstm = input_scaled.reshape((1, 1, len(FEATURES)))

            # b3. Dự báo (Log space)
            pred_log = lstm_specialist.predict(input_lstm, verbose=0)[0][0]

            # b4. Chuyển ngược lại giá trị thực (Inverse Log)
            final_rain = float(np.expm1(pred_log))

            # b5. Logic hậu xử lý (Post-processing) cho "Hợp lý với đời thực"
            if final_rain < 0: final_rain = 0.0

            # Logic: Nếu độ ẩm thấp mà RF vẫn báo mưa (do sai số), ép mưa nhỏ lại
            if hum < 70.0 and final_rain > 5.0:
                final_rain = final_rain * 0.2 # Giảm mưa ảo

            # Logic: Nếu Storm Injection bật, đảm bảo mưa không quá bé
            if is_storm_condition and final_rain < 10.0:
                final_rain = 10.0 + (wind * 0.5) # Ép lên mức mưa vừa

            # Tạo thông báo
            if final_rain > 100: msg = "Mưa đặc biệt lớn (Nguy hiểm)"
            elif final_rain > 50: msg = "Mưa rất to / Giông bão"
            elif final_rain > 25: msg = "Mưa to"
            elif final_rain > 10: msg = "Mưa vừa"
            else: msg = "Mưa nhỏ / Mưa rào nhẹ"

        logger.info(f"🎯 Kết quả: {final_rain:.2f}mm ({msg}) - Prob: {rain_prob:.2f}")

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