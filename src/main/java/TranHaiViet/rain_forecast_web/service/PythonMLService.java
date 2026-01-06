package TranHaiViet.rain_forecast_web.service;

import TranHaiViet.rain_forecast_web.dto.ModelInfoResponse;
import TranHaiViet.rain_forecast_web.dto.PredictionRequest;
import TranHaiViet.rain_forecast_web.dto.PredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PythonMLService {

    private final RestTemplate restTemplate;

    @Value("${app.python-api.url}")
    private String pythonApiUrl;

    private String getBaseUrl() {
        return pythonApiUrl.replace("/predict", "");
    }

    public PredictionResponse getPredictionFromPython(PredictionRequest requestPayload) {
        log.info("Đang gửi yêu cầu dự báo sang Python [URL: {}]: {}", pythonApiUrl, requestPayload);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PredictionRequest> requestEntity = new HttpEntity<>(requestPayload, headers);

            ResponseEntity<PredictionResponse> responseEntity = restTemplate.postForEntity(
                    pythonApiUrl,
                    requestEntity,
                    PredictionResponse.class
            );
            return responseEntity.getBody();

        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng khi gọi API Python: {}", e.getMessage());
            return PredictionResponse.builder()
                    .predictedRainfall(-1.0)
                    .message("Lỗi kết nối Python: " + e.getMessage())
                    .statusCode(500)
                    .build();
        }
    }

    public ModelInfoResponse getModelInfo() {
        String url = getBaseUrl() + "/model-info";
        log.info("Gọi API lấy thông tin Model: {}", url);
        try {
            return restTemplate.getForObject(url, ModelInfoResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.error("🚨 LỖI 404: File app.py đang chạy là bản cũ! Vui lòng Stop và Run lại file app.py mới.");
            return null;
        } catch (Exception e) {
            log.error("Không lấy được thông tin Model: {}", e.getMessage());
            return null;
        }
    }
}