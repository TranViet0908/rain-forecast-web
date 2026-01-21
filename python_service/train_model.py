import pandas as pd
import numpy as np
import joblib
import os
import warnings
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, r2_score, mean_squared_error
from sklearn.preprocessing import MinMaxScaler
from sklearn.utils import resample

# TensorFlow / Keras cho LSTM
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout
from tensorflow.keras.optimizers import Adam

warnings.filterwarnings('ignore')

# CẤU HÌNH
DATA_FILE = 'weather_data_1984_2024.csv'
MODEL_DIR = 'models' # Thư mục chứa model
if not os.path.exists(MODEL_DIR):
    os.makedirs(MODEL_DIR)

# Mapping tọa độ (Để tính đặc trưng địa lý)
LOCATIONS_COORDS = {
    "Thanh Hoa": {"lat": 19.807, "lon": 105.776},
    "Nghe An": {"lat": 18.673, "lon": 105.676},
    "Ha Tinh": {"lat": 18.337, "lon": 105.903},
    "Quang Tri Gop": {"lat": 16.820, "lon": 107.098},
    "Hue": {"lat": 16.463, "lon": 107.590},
    "Da Nang Gop": {"lat": 16.054, "lon": 108.202},
    "Quang Ngai Gop": {"lat": 15.120, "lon": 108.792},
    "Gia Lai Gop": {"lat": 13.782, "lon": 109.219},
    "Dak Lak Gop": {"lat": 12.666, "lon": 108.038},
    "Khanh Hoa Gop": {"lat": 12.238, "lon": 109.196},
    "Lam Dong Gop": {"lat": 11.940, "lon": 108.458}
}

def prepare_data(df):
    """
    Tiền xử lý dữ liệu và tạo các đặc trưng vật lý (Feature Engineering)
    Khớp với Chương 2 Báo cáo
    """
    df['date'] = pd.to_datetime(df['date'].astype(str), format='%Y%m%d', errors='coerce')
    df = df.sort_values(by=['location_name', 'date'])

    # 1. Tính toán các chỉ số vật lý
    # Công thức Magnus cải tiến cho Dew Point
    a, b = 17.27, 237.7
    df['dew_point'] = (b * ((a * df['temperature']) / (b + df['temperature']) + np.log(df['humidity'] / 100.0))) / \
                      (a - ((a * df['temperature']) / (b + df['temperature']) + np.log(df['humidity'] / 100.0)))

    df['lst_minus_temp'] = df['lst'] - df['temperature']
    df['heat_index'] = df['temperature'] * df['humidity'] # Công thức giản lược

    # 2. Tạo đặc trưng trễ (Lag Features) - Quan trọng cho LSTM & RF
    df['rain_lag_1'] = df.groupby('location_name')['rainfall'].shift(1).fillna(0)
    df['rain_mean_3d'] = df.groupby('location_name')['rainfall'].transform(lambda x: x.rolling(3).mean().shift(1)).fillna(0)

    df['temp_change'] = df.groupby('location_name')['temperature'].diff().fillna(0)
    df['hum_change'] = df.groupby('location_name')['humidity'].diff().fillna(0)

    # 3. Mã hóa thời gian (Cyclical Encoding)
    df['day_sin'] = np.sin(2 * np.pi * df['date'].dt.dayofyear / 365.0)
    df['day_cos'] = np.cos(2 * np.pi * df['date'].dt.dayofyear / 365.0)

    # 4. Gán tọa độ
    df['lat'] = df['location_name'].map(lambda x: LOCATIONS_COORDS.get(x, {}).get('lat', 0))
    df['lon'] = df['location_name'].map(lambda x: LOCATIONS_COORDS.get(x, {}).get('lon', 0))

    return df.dropna()

def build_lstm_model(input_shape):
    """
    Kiến trúc mạng LSTM đề xuất (Khớp Báo cáo mục 2.2.2)
    """
    model = Sequential()
    # LSTM Layer 1: Return sequences để giữ thông tin chuỗi
    model.add(LSTM(64, activation='relu', return_sequences=True, input_shape=input_shape))
    model.add(Dropout(0.2)) # Chống Overfitting

    # LSTM Layer 2
    model.add(LSTM(32, activation='relu'))

    # Output Layer
    model.add(Dense(1)) # Dự báo 1 giá trị (Lượng mưa)

    model.compile(optimizer=Adam(learning_rate=0.001), loss='mse')
    return model

def train():
    print("--> [1] Loading & Preprocessing Data...")
    if not os.path.exists(DATA_FILE):
        print(f"Lỗi: Không tìm thấy file {DATA_FILE}")
        return

    df = pd.read_csv(DATA_FILE)
    df = prepare_data(df)

    # Danh sách đặc trưng (Feature List) - 15 features
    features = [
        'lst', 'humidity', 'temperature', 'wind_speed',
        'lat', 'lon', 'day_sin', 'day_cos',
        'dew_point', 'lst_minus_temp', 'heat_index',
        'temp_change', 'hum_change', 'rain_lag_1', 'rain_mean_3d'
    ]
    target = 'rainfall'

    # =========================================================================
    # GIAI ĐOẠN 1: HUẤN LUYỆN GATEKEEPER (RANDOM FOREST CLASSIFIER)
    # =========================================================================
    print("--> [2] Training Gatekeeper (Random Forest)...")

    # Label: Mưa > 0.1mm là 1 (Có mưa), ngược lại là 0
    y_class = (df[target] > 0.1).astype(int)

    X_train_c, X_test_c, y_train_c, y_test_c = train_test_split(df[features], y_class, test_size=0.2, random_state=42)

    rf_classifier = RandomForestClassifier(n_estimators=100, max_depth=15, class_weight='balanced', random_state=42, n_jobs=-1)
    rf_classifier.fit(X_train_c, y_train_c)

    print(f"    + Gatekeeper Accuracy: {accuracy_score(y_test_c, rf_classifier.predict(X_test_c))*100:.2f}%")

    # =========================================================================
    # GIAI ĐOẠN 2: HUẤN LUYỆN SPECIALIST (LSTM)
    # =========================================================================
    print("--> [3] Training Specialist (LSTM)...")

    # Lọc dữ liệu: Chỉ lấy các ngày CÓ MƯA để train LSTM (để nó học cách dự báo lượng mưa)
    rainy_df = df[df[target] > 0.1].copy()

    # --- Kỹ thuật Upsampling (Xử lý mất cân bằng) ---
    # Nhân bản dữ liệu mưa to để LSTM học kỹ hơn các tình huống bão
    heavy_rain = rainy_df[rainy_df[target] > 25]
    extreme_rain = rainy_df[rainy_df[target] > 50]

    heavy_upsampled = resample(heavy_rain, replace=True, n_samples=len(heavy_rain)*10, random_state=42)
    extreme_upsampled = resample(extreme_rain, replace=True, n_samples=len(extreme_rain)*50, random_state=42)

    final_train_df = pd.concat([rainy_df, heavy_upsampled, extreme_upsampled])

    # Chuẩn bị dữ liệu cho LSTM
    X_reg = final_train_df[features].values
    # Log transform target để giảm độ lệch (skewness)
    y_reg = np.log1p(final_train_df[target].values)

    # Scaling (CỰC KỲ QUAN TRỌNG VỚI LSTM)
    scaler = MinMaxScaler(feature_range=(0, 1))
    X_reg_scaled = scaler.fit_transform(X_reg)

    # Reshape input cho LSTM: [samples, time_steps, features]
    # Ở đây dùng time_steps=1 vì dữ liệu web gửi lên là từng dòng đơn lẻ
    X_reg_reshaped = X_reg_scaled.reshape((X_reg_scaled.shape[0], 1, X_reg_scaled.shape[1]))

    # Split
    X_train_l, X_test_l, y_train_l, y_test_l = train_test_split(X_reg_reshaped, y_reg, test_size=0.1, random_state=42)

    # Build & Train LSTM
    lstm_model = build_lstm_model(input_shape=(1, len(features)))

    # Train
    history = lstm_model.fit(
        X_train_l, y_train_l,
        epochs=30, # Số vòng lặp
        batch_size=64,
        validation_split=0.1,
        verbose=1
    )

    # Đánh giá sơ bộ
    y_pred_log = lstm_model.predict(X_test_l)
    r2 = r2_score(y_test_l, y_pred_log)
    print(f"    + Specialist (LSTM) R2 Score (Log space): {r2:.4f}")

    # =========================================================================
    # LƯU TRỮ MODEL
    # =========================================================================
    print("--> [4] Saving Models...")

    # 1. Lưu RF và Scaler bằng Joblib
    joblib.dump(rf_classifier, os.path.join(MODEL_DIR, 'rf_gatekeeper.pkl'))
    joblib.dump(scaler, os.path.join(MODEL_DIR, 'scaler.pkl'))

    # 2. Lưu LSTM bằng Keras format (.h5 hoặc .keras)
    lstm_model.save(os.path.join(MODEL_DIR, 'lstm_specialist.h5'))

    print("✅ Training Complete! All models saved in 'models/' directory.")

if __name__ == "__main__":
    train()