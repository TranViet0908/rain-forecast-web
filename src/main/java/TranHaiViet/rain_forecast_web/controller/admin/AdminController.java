package TranHaiViet.rain_forecast_web.controller.admin;

import TranHaiViet.rain_forecast_web.dto.UpdateActualRequest;
import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import TranHaiViet.rain_forecast_web.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin") // Prefix chung cho controller này
@RequiredArgsConstructor
public class AdminController {

    private final WeatherService weatherService;

    // 1. Hiển thị trang Admin (Load dữ liệu từ Server thay vì JS mock)
    @GetMapping
    public String showAdminPanel(Model model) {
        List<PredictionHistory> pendingList = weatherService.getPendingHistories();
        List<PredictionHistory> completedList = weatherService.getCompletedHistories();

        model.addAttribute("pendingList", pendingList);

        // Tính toán thống kê đơn giản cho Admin
        model.addAttribute("pendingCount", pendingList.size());
        model.addAttribute("confirmedCount", completedList.size());

        // Tính sai số trung bình (nếu có dữ liệu)
        double avgError = completedList.stream()
                .mapToDouble(h -> Math.abs(h.getPredictedRainfall() - h.getActualRainfall()))
                .average()
                .orElse(0.0);
        model.addAttribute("avgError", String.format("%.1f", avgError));

        return "admin"; // Trả về templates/admin.html
    }

    // 2. Xử lý cập nhật (AJAX gọi vào đây) - KHÔNG DÙNG /api
    @PostMapping("/update-actual")
    @ResponseBody
    public ResponseEntity<?> updateActualRainfall(@RequestBody UpdateActualRequest request) {
        try {
            weatherService.updateActualRainfall(request.getId(), request.getActualRainfall());
            return ResponseEntity.ok(Map.of("message", "Cập nhật thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}