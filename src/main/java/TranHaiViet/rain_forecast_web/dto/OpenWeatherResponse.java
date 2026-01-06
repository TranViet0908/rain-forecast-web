package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Bỏ qua các trường không cần thiết (clouds, sys...)
public class OpenWeatherResponse {

    // Map object "main" trong JSON
    @JsonProperty("main")
    private Main main;

    // Map object "wind" trong JSON
    @JsonProperty("wind")
    private Wind wind;

    // Class con để hứng dữ liệu bên trong "main"
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Main {
        @JsonProperty("temp")
        private Double temp;

        @JsonProperty("humidity")
        private Double humidity;
    }

    // Class con để hứng dữ liệu bên trong "wind"
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wind {
        @JsonProperty("speed")
        private Double speed;
    }
}