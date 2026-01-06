package TranHaiViet.rain_forecast_web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapDataResponse {
    private Long id;
    private String name;
    private Double lat;
    private Double lon;
    private Double predictedRain; // Lượng mưa dự báo mới nhất
    private String status;        // "SAFE", "WARNING", "DANGER"

    // --- MỚI: Thông tin chi tiết (Lazy Load) ---
    private Double currentTemp;      // Nhiệt độ không khí
    private Double currentLst;       // Nhiệt độ bề mặt (ước lượng)
    private Double currentHumidity;  // Độ ẩm
    private Double currentWind;      // Tốc độ gió
    private String addressDetail;    // Tên Phường/Xã/Thị trấn
}