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
    public String showDashboard(Model model) {
        List<Location> locations = weatherService.getAllLocations();
        model.addAttribute("locations", locations);
        model.addAttribute("predictionRequest", new PredictionRequest());
        return "index";
    }

    @GetMapping("/history")
    public String showHistory(@RequestParam(required = false) Long locationId, Model model) {
        model.addAttribute("locations", weatherService.getAllLocations());
        List<PredictionHistory> historyList;

        if (locationId != null) {
            historyList = weatherService.getHistoryByLocation(locationId);
            model.addAttribute("selectedLocationId", locationId);
        } else {
            historyList = weatherService.getAllHistories();
        }

        model.addAttribute("histories", historyList);
        return "history";
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
}