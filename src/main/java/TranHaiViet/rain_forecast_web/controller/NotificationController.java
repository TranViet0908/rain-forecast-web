package TranHaiViet.rain_forecast_web.controller;

import TranHaiViet.rain_forecast_web.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {
    private final AlertService alertService;

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestParam String email, @RequestParam Long locationId) {
        try {
            alertService.subscribe(email, locationId);
            return ResponseEntity.ok(Map.of("message", "Đăng ký thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}