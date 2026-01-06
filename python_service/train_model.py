import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, r2_score, accuracy_score
from sklearn.utils import resample
import joblib
import os
import warnings

warnings.filterwarnings('ignore')

DATA_FILE = 'weather_data_1984_2024.csv'
MODEL_PATH = 'random_forest_model.pkl'

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
    df['date'] = pd.to_datetime(df['date'].astype(str), format='%Y%m%d', errors='coerce')
    df = df.sort_values(by=['location_name', 'date'])

    # Feature Engineering (Vật lý & LST)
    a, b = 17.27, 237.7
    df['dew_point'] = (b * ((a * df['temperature']) / (b + df['temperature']) + np.log(df['humidity'] / 100.0))) / (a - ((a * df['temperature']) / (b + df['temperature']) + np.log(df['humidity'] / 100.0)))

    df['lst_minus_temp'] = df['lst'] - df['temperature']
    df['heat_index'] = df['temperature'] * df['humidity']

    df['rain_lag_1'] = df.groupby('location_name')['rainfall'].shift(1).fillna(0)
    df['rain_mean_3d'] = df.groupby('location_name')['rainfall'].transform(lambda x: x.rolling(3).mean().shift(1)).fillna(0)

    df['temp_change'] = df.groupby('location_name')['temperature'].diff().fillna(0)
    df['hum_change'] = df.groupby('location_name')['humidity'].diff().fillna(0)

    df['day_sin'] = np.sin(2 * np.pi * df['date'].dt.dayofyear / 365.0)
    df['day_cos'] = np.cos(2 * np.pi * df['date'].dt.dayofyear / 365.0)
    df['lat'] = df['location_name'].map(lambda x: LOCATIONS_COORDS.get(x, {}).get('lat', 0))
    df['lon'] = df['location_name'].map(lambda x: LOCATIONS_COORDS.get(x, {}).get('lon', 0))

    return df.dropna()

def train():
    print("--> [1] Loading Data...")
    if not os.path.exists(DATA_FILE): return
    df = pd.read_csv(DATA_FILE)
    df = prepare_data(df)

    features = [
        'lst', 'humidity', 'temperature', 'wind_speed',
        'lat', 'lon', 'day_sin', 'day_cos',
        'dew_point', 'lst_minus_temp', 'heat_index',
        'temp_change', 'hum_change', 'rain_lag_1', 'rain_mean_3d'
    ]
    target = 'rainfall'

    # ---------------------------------------------------------
    # GIAI ĐOẠN 1: MÔ HÌNH HỖ TRỢ (RF CLASSIFIER)
    # Nhiệm vụ: Chỉ xác định MƯA (1) hay KHÔNG MƯA (0)
    # ---------------------------------------------------------
    print("--> [2] Training Support Model (RF Classifier)...")

    # Tạo nhãn: Mưa > 0.1mm là có mưa
    y_class = (df[target] > 0.1).astype(int)

    X_train_c, X_test_c, y_train_c, y_test_c = train_test_split(df[features], y_class, test_size=0.2, random_state=42)

    # Class_weight='balanced' giúp nó chú ý kỹ đến ngày mưa (vốn ít hơn ngày nắng)
    rf_classifier = RandomForestClassifier(n_estimators=100, max_depth=15, class_weight='balanced', random_state=42, n_jobs=-1)
    rf_classifier.fit(X_train_c, y_train_c)

    acc = accuracy_score(y_test_c, rf_classifier.predict(X_test_c))
    print(f"    + Accuracy (Phân loại): {acc*100:.2f}%")

    # ---------------------------------------------------------
    # GIAI ĐOẠN 2: MÔ HÌNH CHÍNH (RF REGRESSOR)
    # Nhiệm vụ: Dự báo CHÍNH XÁC lượng mưa (khi biết trời sẽ mưa)
    # ---------------------------------------------------------
    print("--> [3] Training Main Model (RF Regressor)...")

    # Lọc: Chỉ lấy những ngày CÓ MƯA để train cho Main Model
    # Điều này giúp model "Chuyên gia" không bị nhiễu bởi ngày nắng
    rainy_df = df[df[target] > 0.1]

    # Upsampling: Nhân bản các cơn bão/mưa to để model học kỹ
    heavy_rain = rainy_df[rainy_df[target] > 20]
    extreme_rain = rainy_df[rainy_df[target] > 50]

    # Nhân dữ liệu bão lên gấp 5 và 10 lần
    heavy_upsampled = resample(heavy_rain, replace=True, n_samples=len(heavy_rain)*20, random_state=42)
    extreme_upsampled = resample(extreme_rain, replace=True, n_samples=len(extreme_rain)*100, random_state=42)

    # Gộp lại thành tập train chuyên sâu
    final_rainy_df = pd.concat([rainy_df, heavy_upsampled, extreme_upsampled])

    X_reg = final_rainy_df[features]
    y_reg = np.log1p(final_rainy_df[target]) # Log transform

    X_train_r, X_test_r, y_train_r, y_test_r = train_test_split(X_reg, y_reg, test_size=0.1, random_state=42)

    rf_regressor = RandomForestRegressor(n_estimators=300, max_depth=30, random_state=42, n_jobs=-1)
    rf_regressor.fit(X_train_r, y_train_r)

    # Đánh giá trên tập test gốc (chưa upsample) của những ngày mưa
    X_test_orig = rainy_df[features]
    y_test_orig = rainy_df[target]
    y_pred_orig = np.expm1(rf_regressor.predict(X_test_orig))

    r2 = r2_score(y_test_orig, y_pred_orig)
    print(f"    + R2 Score (Độ chính xác lượng mưa): {r2:.4f}")

    # ---------------------------------------------------------
    # LƯU CẢ 2 MÔ HÌNH VÀO 1 FILE
    # ---------------------------------------------------------
    print(f"--> [4] Saving Hybrid RF System to {MODEL_PATH}")
    models = {
        'gatekeeper': rf_classifier, # Model hỗ trợ
        'specialist': rf_regressor   # Model chính
    }
    joblib.dump(models, MODEL_PATH)

if __name__ == "__main__":
    train()