package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PredictionRequest {

    @JsonProperty("location_name")
    private String locationName;

    @JsonProperty("lst")
    private Double lst;

    @JsonProperty("humidity")
    private Double humidity;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lon")
    private Double lon;

    // --- QUAN TRỌNG: PHẢI ĐẶT TÊN LÀ inputWindSpeed ---
    // Vì bên WeatherService đang gọi hàm setInputWindSpeed()
    // Nếu bạn đặt là "windSpeed" thì Lombok chỉ sinh ra setWindSpeed() -> Gây lỗi
    @JsonProperty("wind_speed")
    private Double inputWindSpeed;

    // Trường này để nhận biết user chọn đơn vị gì ("kmh" hoặc "ms")
    @JsonProperty("wind_unit")
    private String windUnit;
}