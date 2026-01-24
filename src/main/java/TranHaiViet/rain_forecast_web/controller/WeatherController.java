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
    public String index(Model model) {
        // [CŨ] Lấy từ Database (Dữ liệu có thể bị cũ/sai)
        // List<PredictionHistory> forecasts = weatherService.getLatestForecasts();

        // [MỚI] Gọi API Realtime + AI ngay lập tức
        List<PredictionHistory> forecasts = weatherService.getRealtimeForecastForAll();

        model.addAttribute("forecasts", forecasts);
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

        // Người dùng test trên web -> saveToDb = false (KHÔNG LƯU)
        // Để tránh làm bẩn dữ liệu trên bản đồ
        PredictionResponse response = weatherService.predictRainfall(locationId, request, false);

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
    // 3. TRANG CHI TIẾT (DETAIL PAGE)
    // ==========================================
    // [THAY THẾ HOẶC THÊM VÀO WeatherController.java]
    @GetMapping("/forecast/details")
    public String showDetailsPage(@RequestParam Long locationId, Model model) {
        // 1. Lấy thông tin chi tiết (Nhiệt độ, độ ẩm, gió hiện tại + Mưa dự báo hôm nay)
        var locationDetail = weatherService.getMapLocationDetail(locationId);
        model.addAttribute("detail", locationDetail);

        // 2. Lấy dữ liệu dự báo 5 ngày tới (Cho biểu đồ)
        List<PredictionResponse> forecast5Days = weatherService.getMultiDayForecast(locationId);
        model.addAttribute("forecastList", forecast5Days);

        // 3. LOGIC CHỌN ẢNH NỀN HERO (Mapping tên tỉnh -> tên file ảnh)
        String imgName = "default_rain.jpg"; // Ảnh mặc định phòng hờ
        String name = locationDetail.getName();

        if (name.contains("Thanh Hóa")) imgName = "samson.jpg";
        else if (name.contains("Nghệ An")) imgName = "quebac.jpg";
        else if (name.contains("Hà Tĩnh")) imgName = "hatinh.jpg";
        else if (name.contains("Quảng Trị")) imgName = "thanhco.jpg";
        else if (name.contains("Huế") || name.contains("Thừa Thiên")) imgName = "hue.jpg";
        else if (name.contains("Đà Nẵng")) imgName = "cauvang.jpg";
        else if (name.contains("Quảng Ngãi")) imgName = "lyson.jpg";
        else if (name.contains("Gia Lai")) imgName = "bienho.jpg";
        else if (name.contains("Đắk Lắk")) imgName = "buondon.jpg";
        else if (name.contains("Khánh Hòa")) imgName = "nhatrang.jpg";
        else if (name.contains("Lâm Đồng")) imgName = "dalat.jpg";
            // Các tỉnh khác nếu có...
        else if (name.contains("Bình Định")) imgName = "quynhon.jpg";
        else if (name.contains("Phú Yên")) imgName = "ghenhdadia.jpg";

        model.addAttribute("bgImage", imgName);

        return "details";
    }
}