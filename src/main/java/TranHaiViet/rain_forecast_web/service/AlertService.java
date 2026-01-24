package TranHaiViet.rain_forecast_web.service;

import TranHaiViet.rain_forecast_web.dto.PredictionRequest;
import TranHaiViet.rain_forecast_web.dto.PredictionResponse;
import TranHaiViet.rain_forecast_web.entity.Location;
import TranHaiViet.rain_forecast_web.entity.Subscriber;
import TranHaiViet.rain_forecast_web.repository.LocationRepository;
import TranHaiViet.rain_forecast_web.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final WeatherService weatherService;
    private final LocationRepository locationRepository;
    private final SubscriberRepository subscriberRepository;
    private final JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // Chạy mỗi 1 giờ (0 phút, mỗi giờ)
    @Scheduled(cron = "0 0 * * * ?") // Chạy mỗi giờ
    public void runAutoAlertSystem() {
        log.info("⏰ BẮT ĐẦU QUÉT HỆ THỐNG CẢNH BÁO TỰ ĐỘNG...");
        List<Location> locations = locationRepository.findAll();

        for (Location loc : locations) {
            try {
                // Lấy thời tiết hiện tại từ API OpenWeatherMap
                PredictionRequest weatherNow = weatherService.getCurrentWeatherFromApi(loc.getId());

                if (weatherNow != null) {
                    // [FIX TẠI ĐÂY]
                    // Gọi hàm mới predictRainfall với tham số saveToDb = true
                    // Vì đây là dữ liệu thật tự động quét, cần lưu để hiển thị lên Map/Home
                    PredictionResponse forecast = weatherService.predictRainfall(loc.getId(), weatherNow, true);

                    Double rain = forecast.getPredictedRainfall();

                    // Nếu mưa > 50mm -> Gửi cảnh báo
                    if (rain >= 50.0) {
                        log.warn("⚠️ CẢNH BÁO: {} mưa to ({}mm). Gửi mail...", loc.getName(), rain);
                        sendAlertToSubscribers(loc, rain);
                    }
                }
            } catch (Exception e) {
                log.error("Lỗi quét trạm {}: {}", loc.getName(), e.getMessage());
            }
        }
    }

    private void sendAlertToSubscribers(Location loc, Double rainAmount) {
        List<Subscriber> subs = subscriberRepository.findByLocationId(loc.getId());
        for (Subscriber sub : subs) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(sub.getEmail());
                message.setSubject("🚨 CẢNH BÁO MƯA LỚN: " + loc.getName().toUpperCase());
                message.setText("Cảnh báo mưa lớn tại " + loc.getName() + ".\n" +
                        "Dự báo lượng mưa: " + rainAmount + " mm.\n" +
                        "Vui lòng kiểm tra nhà cửa và hạn chế ra đường.");
                emailSender.send(message);
            } catch (Exception e) {
                log.error("Lỗi gửi mail: {}", e.getMessage());
            }
        }
    }

    public void subscribe(String email, Long locationId) {
        Location loc = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Địa điểm không tồn tại"));

        if(subscriberRepository.findByEmailAndLocationId(email, locationId).isEmpty()) {
            subscriberRepository.save(Subscriber.builder().email(email).location(loc).build());
        }
    }
}