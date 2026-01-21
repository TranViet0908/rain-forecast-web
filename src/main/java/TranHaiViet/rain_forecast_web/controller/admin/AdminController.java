package TranHaiViet.rain_forecast_web.controller.admin;

import TranHaiViet.rain_forecast_web.dto.UpdateActualRequest;
import TranHaiViet.rain_forecast_web.entity.Location;
import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import TranHaiViet.rain_forecast_web.entity.Subscriber;
import TranHaiViet.rain_forecast_web.repository.SubscriberRepository;
import TranHaiViet.rain_forecast_web.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final WeatherService weatherService;
    private final SubscriberRepository subscriberRepository;
    private static final int PAGE_SIZE = 10; // Số dòng mỗi trang

    // =======================================================
    // 1. DASHBOARD
    // =======================================================
    @GetMapping(value = {"", "/dashboard"})
    public String dashboard(Model model) {
        // 1. Các chỉ số cơ bản (KPIs)
        long pendingCount = weatherService.getPendingHistoriesPaginated(0, 1).getTotalElements();
        long confirmedCount = weatherService.getCompletedCount();
        double avgError = weatherService.getAverageError();
        long subCount = weatherService.getTotalSubscribers(); // [MỚI]

        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("avgError", String.format("%.1f", avgError));
        model.addAttribute("subCount", subCount);

        // 2. Biểu đồ xu hướng (Line Chart - Giữ nguyên)
        model.addAttribute("chartData", weatherService.getAllHistories());

        // 3. [MỚI] Biểu đồ phân bố mưa (Pie Chart Data)
        // List gồm 4 số: [Không mưa, Mưa nhỏ, Mưa vừa, Mưa to]
        List<Long> rainDist = weatherService.getRainDistribution();
        model.addAttribute("rainDist", rainDist);

        // 4. [MỚI] Top điểm nóng ngày mai (Risk List)
        // Logic: Nếu ngày mai chưa có dữ liệu, lấy ngày hôm nay để demo cho đẹp
        List<PredictionHistory> topRisks = weatherService.getTopRisksForTomorrow();
        if (topRisks.isEmpty()) {
            topRisks = weatherService.getTopRisksForDate(LocalDate.now());
        }
        model.addAttribute("topRisks", topRisks);

        model.addAttribute("pageTitle", "Dashboard");
        return "admin/dashboard";
    }

    // =======================================================
    // 2. XÁC THỰC KẾT QUẢ (VERIFICATION) - Có Lọc
    // =======================================================
    @GetMapping("/verification")
    public String verificationPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Double minRain,
            @RequestParam(required = false) Double maxRain,
            @RequestParam(required = false) String keyword,
            Model model) {

        Page<PredictionHistory> data = weatherService.searchPendingAdvanced(
                locationId, startDate, endDate, minRain, maxRain, keyword, page, PAGE_SIZE);

        List<Location> locations = weatherService.getAllLocations();

        model.addAttribute("dataPage", data);
        model.addAttribute("locations", locations);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageTitle", "Xác thực kết quả thực tế");

        // Trả lại các tham số để giữ trạng thái trên Form
        model.addAttribute("locationId", locationId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minRain", minRain);
        model.addAttribute("maxRain", maxRain);
        model.addAttribute("keyword", keyword);

        return "admin/verification";
    }

    // =======================================================
    // 3. LỊCH SỬ DỰ BÁO (HISTORY) - Có Lọc
    // =======================================================
    @GetMapping("/history")
    public String historyPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Double minRain,
            @RequestParam(required = false) Double maxRain,
            @RequestParam(required = false) String status, // COMPLETED, PENDING
            @RequestParam(required = false) String keyword,
            Model model) {

        Page<PredictionHistory> data = weatherService.searchHistoryAdvanced(
                locationId, startDate, endDate, minRain, maxRain, status, keyword, page, PAGE_SIZE);

        List<Location> locations = weatherService.getAllLocations();

        model.addAttribute("dataPage", data);
        model.addAttribute("locations", locations);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageTitle", "Toàn bộ lịch sử dự báo");

        // Giữ trạng thái form
        model.addAttribute("locationId", locationId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minRain", minRain);
        model.addAttribute("maxRain", maxRain);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);

        return "admin/history";
    }

    // =======================================================
    // 4. CÁC API XỬ LÝ DỮ LIỆU (AJAX)
    // =======================================================

    // API Cập nhật thực tế (Dùng cho trang Verification)
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

    // API Xóa bản ghi (Dùng chung cho cả History và Verification)
    @PostMapping("/delete-history")
    @ResponseBody
    public ResponseEntity<?> deleteHistory(@RequestBody Map<String, Long> payload) {
        try {
            Long id = payload.get("id");
            if (id == null) throw new RuntimeException("ID không hợp lệ");

            weatherService.deleteHistory(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    // =======================================================
    // 5. CÁC TRANG KHÁC (Placeholder)
    // =======================================================

    @GetMapping("/subscribers")
    public String subscribersPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String keyword,
            Model model) {

        // Xử lý keyword rỗng
        if (keyword != null && keyword.trim().isEmpty()) keyword = null;

        // Gọi Repository lấy dữ liệu phân trang
        Page<Subscriber> data = subscriberRepository.searchSubscribers(
                locationId, keyword, PageRequest.of(page, PAGE_SIZE));

        model.addAttribute("dataPage", data);
        model.addAttribute("locations", weatherService.getAllLocations()); // Để đổ vào dropdown
        model.addAttribute("currentPage", page);
        model.addAttribute("pageTitle", "Danh sách đăng ký");

        // Giữ trạng thái bộ lọc
        model.addAttribute("locationId", locationId);
        model.addAttribute("keyword", keyword);

        return "admin/subscribers";
    }

    @PostMapping("/delete-subscriber")
    @ResponseBody
    public ResponseEntity<?> deleteSubscriber(@RequestBody Map<String, Long> payload) {
        try {
            Long id = payload.get("id");
            subscriberRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Đã hủy đăng ký thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi: " + e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        model.addAttribute("pageTitle", "Cấu hình hệ thống");
        return "admin/settings";
    }
}