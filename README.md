# Rain Forecast Web - Dự báo lượng mưa Miền Trung

Ứng dụng web dự báo lượng mưa tại các tỉnh miền Trung Việt Nam, kết hợp sức mạnh của **Spring Boot (Java)** và **Machine Learning (Python Random Forest + LST)**.

## 🚀 Công nghệ sử dụng

* **Backend:** Java 21, Spring Boot 3.5.8
* **Frontend:** Thymeleaf, Bootstrap (HTML/CSS/JS)
* **Database:** MySQL
* **Machine Learning:** Python 3.x, Scikit-learn, Pandas, Flask API
* **Dữ liệu:** OpenWeatherMap API & Dữ liệu lịch sử 1984-2024

## ⚙️ Cài đặt & Chạy dự án

### 1. Cấu hình Database
* Tạo database MySQL tên: `rain_forecast_db`
* Import file `rain_forecast_db.sql` (nếu có) hoặc để Hibernate tự tạo bảng.
* Cập nhật username/password trong `application.properties`.

### 2. Chạy Python Service
Cài đặt thư viện:
```bash
cd python_service
pip install -r requirements.txt