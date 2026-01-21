package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenWeatherForecastResponse {

    @JsonProperty("list")
    private List<ForecastItem> list;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForecastItem {
        @JsonProperty("dt")
        private Long dt; // Thời gian (Unix timestamp)

        @JsonProperty("main")
        private OpenWeatherResponse.Main main; // Tái sử dụng class Main cũ

        @JsonProperty("wind")
        private OpenWeatherResponse.Wind wind; // Tái sử dụng class Wind cũ

        @JsonProperty("dt_txt")
        private String dtTxt; // Ví dụ: "2026-01-13 12:00:00"
    }
}