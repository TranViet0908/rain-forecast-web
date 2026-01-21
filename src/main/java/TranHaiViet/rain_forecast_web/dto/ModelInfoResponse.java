package TranHaiViet.rain_forecast_web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ModelInfoResponse {
    @JsonProperty("algorithm")
    private String algorithm;

    @JsonProperty("metrics")
    private Map<String, Object> metrics;

    @JsonProperty("feature_importance")
    private List<FeatureScore> featureImportance;

    @Data
    public static class FeatureScore {
        private String name;
        private Double score;
    }
}