package TranHaiViet.rain_forecast_web.controller;

import TranHaiViet.rain_forecast_web.dto.ModelInfoResponse;
import TranHaiViet.rain_forecast_web.dto.PredictionRequest;
import TranHaiViet.rain_forecast_web.dto.PredictionResponse;
import TranHaiViet.rain_forecast_web.entity.Location;
import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import TranHaiViet.rain_forecast_web.service.PythonMLService;
import TranHaiViet.rain_forecast_web.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WeatherController {

    private final WeatherService weatherService;
    private final PythonMLService pythonMLService;

    // ==========================================
    // 1. VIEW CONTROLLER
    // ==========================================

    @GetMapping("/")
    public String showLandingPage(Model model) {
        // 1. Lấy danh sách dự báo mới nhất cho các tỉnh
        List<PredictionHistory> forecasts = weatherService.getLatestForecasts();
        model.addAttribute("forecasts", forecasts);

        // 2. Tính toán cập nhật mới nhất (để hiển thị thời gian update)
        String lastUpdate = forecasts.isEmpty() ? "Chưa có dữ liệu" :
                forecasts.get(0).getPredictionTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        model.addAttribute("lastUpdate", lastUpdate);

        return "index";
    }

    @GetMapping("/prediction")
    public String showPredictionPage(Model model) {
        // Lấy danh sách tỉnh thành để đổ vào dropdown chọn khu vực
        List<Location> locations = weatherService.getAllLocations();
        model.addAttribute("locations", locations);

        // Tạo object rỗng để hứng dữ liệu form
        model.addAttribute("predictionRequest", new PredictionRequest());

        // Trả về file templates/prediction.html
        return "prediction";
    }

    @GetMapping("/map")
    public String showMapPage() {
        return "map";
    }

    // --- MỚI: TRANG KHOA HỌC & MÔ HÌNH ---
    @GetMapping("/methodology")
    public String showMethodologyPage(Model model) {
        // Gọi Python để lấy dữ liệu Feature Importance thật
        ModelInfoResponse modelInfo = pythonMLService.getModelInfo();
        model.addAttribute("modelInfo", modelInfo);
        return "methodology";
    }

    // --- MỚI: TRANG CẢNH BÁO & SINH TỒN ---
    @GetMapping("/safety")
    public String showSafetyPage() {
        return "safety";
    }

    // ==========================================
    // 2. REST API
    // ==========================================

    @PostMapping("/api/predict")
    @ResponseBody
    public ResponseEntity<PredictionResponse> predictRainfall(
            @RequestParam Long locationId,
            @RequestBody PredictionRequest request) {
        log.info("Nhận request dự báo cho Location ID: {}", locationId);
        PredictionResponse response = weatherService.predictAndSave(locationId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/current-weather")
    @ResponseBody
    public ResponseEntity<?> getCurrentWeather(@RequestParam Long locationId) {
        try {
            log.info("Đang lấy thời tiết cho Location ID: {}", locationId);
            PredictionRequest weatherData = weatherService.getCurrentWeatherFromApi(locationId);
            return ResponseEntity.ok(weatherData);
        } catch (Exception e) {
            log.error("Lỗi API Weather: ", e);
            return ResponseEntity.badRequest().body("Lỗi Server: " + e.getMessage());
        }
    }
    @GetMapping("/api/forecast")
    @ResponseBody
    public ResponseEntity<List<PredictionResponse>> getFutureForecast(@RequestParam Long locationId) {
        try {
            List<PredictionResponse> forecast = weatherService.getMultiDayForecast(locationId);
            return ResponseEntity.ok(forecast);
        } catch (Exception e) {
            log.error("Lỗi dự báo tương lai: ", e);
            return ResponseEntity.badRequest().build();
        }
    }
}