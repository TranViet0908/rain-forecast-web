# Rain Forecast Web - Dự báo lượng mưa Miền Trung (Hybrid AI)

Ứng dụng web dự báo lượng mưa chuyên sâu cho khu vực miền Trung Việt Nam, sử dụng mô hình lai ghép (Hybrid Model) giữa **Random Forest** (phân loại mưa) và **Deep Learning LSTM** (dự báo định lượng), kết hợp với kỹ thuật **Storm Injection** để cảnh báo thiên tai.

## 🚀 Công nghệ sử dụng

* **Backend:** Java 21, Spring Boot 3.5.8
* **Frontend:** Thymeleaf, Bootstrap 5, Chart.js, Leaflet Maps
* **Database:** MySQL 8.0+
* **AI/Machine Learning:**
    * Python 3.10+ (Khuyến nghị chạy trên môi trường ảo)
    * **TensorFlow/Keras:** Mạng nơ-ron LSTM
    * **Scikit-learn:** Random Forest Classifier & Regressor
    * **Flask:** API Server kết nối giữa Java và Python

---

## ⚙️ Hướng dẫn Cài đặt & Chạy dự án (Cập nhật 2026)

### 1. Cấu hình Database (MySQL)
1.  Tạo database mới tên: `rain_forecast_db`
2.  Mở file `src/main/resources/application.properties`, cập nhật thông tin đăng nhập:
    ```properties
    spring.datasource.url=jdbc:mysql://localhost:3306/rain_forecast_db?useUnicode=true&characterEncoding=UTF-8
    spring.datasource.username=root
    spring.datasource.password=YOUR_PASSWORD
    ```
3.  Khi chạy ứng dụng Java lần đầu, Hibernate sẽ tự động tạo các bảng cần thiết.

---

### 2. Cấu hình & Chạy Python Service (QUAN TRỌNG)
Do thư viện TensorFlow yêu cầu môi trường sạch để tránh lỗi `DLL load failed`, bạn **bắt buộc** phải sử dụng môi trường ảo (`venv`).

#### Bước 2.1: Khởi tạo môi trường (Chỉ làm 1 lần đầu tiên)
Mở **PowerShell** (Admin) tại thư mục `rain-forecast-web`, chạy lần lượt:

```powershell
# 1. Di chuyển vào thư mục code Python
cd python_service

# 2. Tạo môi trường ảo tên là 'venv'
python -m venv venv

# 3. Kích hoạt môi trường (Bắt buộc: Đầu dòng lệnh phải hiện chữ (venv))
.\venv\Scripts\Activate

# 4. Cài đặt các thư viện chuẩn (TensorFlow CPU bản ổn định)
pip install tensorflow==2.16.1 pandas numpy scikit-learn flask joblib

Bước 2.2: Huấn luyện Mô hình (Train Model)
Vẫn trong cửa sổ PowerShell đang có (venv):
PowerShell
# Lệnh này sẽ tạo ra 2 file: rf_gatekeeper.pkl và lstm_specialist.h5 trong thư mục 'models/'
python train_model.py
Bước 2.3: Chạy Server AI
Sau khi train xong, chạy lệnh sau để mở API dự báo:
PowerShell
python app.py
Server sẽ chạy tại: http://localhost:5000
Lưu ý: Tuyệt đối không tắt cửa sổ PowerShell này khi đang dùng web.

3. Chạy ứng dụng Java (Spring Boot)
Mở dự án bằng IntelliJ IDEA.
Chạy file RainForecastWebApplication.java.
Ứng dụng sẽ chạy tại: http://localhost:8080.

📝 Quy trình sử dụng hàng ngày
Mỗi khi khởi động lại máy tính, bạn cần làm theo thứ tự sau để hệ thống hoạt động:
Bật Python:
Mở PowerShell.
cd python_service
.\venv\Scripts\Activate (Quên lệnh này sẽ bị lỗi DLL).
python app.py
Bật Java: Chạy trong IntelliJ.
Truy cập: Vào localhost:8080 để xem dự báo.

📂 Cấu trúc thư mục chính
python_service/: Chứa mã nguồn AI (Flask, Train, Data).
models/: Chứa file model đã train (.h5, .pkl).
venv/: Môi trường ảo chứa thư viện (không sửa file trong này).
src/main/java/: Mã nguồn Backend Spring Boot.
src/main/resources/templates/: Giao diện HTML (Thymeleaf).