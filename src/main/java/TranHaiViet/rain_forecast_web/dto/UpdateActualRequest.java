package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateActualRequest {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("actual_rainfall")
    private Double actualRainfall;
}