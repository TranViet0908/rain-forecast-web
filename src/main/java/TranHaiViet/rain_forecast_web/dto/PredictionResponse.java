package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponse {

    @JsonProperty("predicted_rainfall")
    private Double predictedRainfall;

    @JsonProperty("message")
    private String message;

    @JsonProperty("status_code")
    private Integer statusCode;
}