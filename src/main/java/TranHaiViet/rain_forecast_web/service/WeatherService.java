package TranHaiViet.rain_forecast_web.service;

import TranHaiViet.rain_forecast_web.dto.MapDataResponse;
import TranHaiViet.rain_forecast_web.dto.OpenWeatherResponse;
import TranHaiViet.rain_forecast_web.dto.PredictionRequest;
import TranHaiViet.rain_forecast_web.dto.PredictionResponse;
import TranHaiViet.rain_forecast_web.entity.Location;
import TranHaiViet.rain_forecast_web.entity.PredictionFeature;
import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import TranHaiViet.rain_forecast_web.repository.LocationRepository;
import TranHaiViet.rain_forecast_web.repository.PredictionFeatureRepository;
import TranHaiViet.rain_forecast_web.repository.PredictionHistoryRepository;
import TranHaiViet.rain_forecast_web.repository.SubscriberRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final LocationRepository locationRepository;
    private final PredictionHistoryRepository predictionHistoryRepository;
    private final PredictionFeatureRepository predictionFeatureRepository;
    private final SubscriberRepository subscriberRepository; // [MỚI] Inject thêm
    private final PythonMLService pythonMLService;
    private static final String OPEN_WEATHER_FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast?lat=%s&lon=%s&appid=%s&units=metric";

    // Thêm RestTemplate để gọi API OpenWeatherMap & Nominatim
    private final RestTemplate restTemplate;

    // API Key của bạn
    private static final String OPEN_WEATHER_API_KEY = "5796abbde9106b7da4febfae8c44c232";
    private static final String OPEN_WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s&units=metric";

    // API Nominatim (OpenStreetMap)
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse?format=json&lat=%s&lon=%s&zoom=14";

    public List<Location> getAllLocations() {
        return locationRepository.findAll();
    }

    public List<PredictionHistory> getAllHistories() {
        return predictionHistoryRepository.findAll();
    }

    public List<PredictionHistory> getHistoryByLocation(Long locationId) {
        return predictionHistoryRepository.findByLocationIdOrderByPredictedForDateDesc(locationId);
    }

    // --- MỚI: API LẤY CHI TIẾT CHO MAP POPUP (Gộp Weather + Address) ---
    public MapDataResponse getMapLocationDetail(Long locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa điểm"));

        // 1. Lấy thông tin dự báo gần nhất
        List<PredictionHistory> histories = getHistoryByLocation(locationId);
        Double predictedRain = histories.isEmpty() ? 0.0 : histories.get(0).getPredictedRainfall();
        String status = "SAFE";
        if (predictedRain > 50) status = "DANGER";
        else if (predictedRain > 10) status = "WARNING";

        // 2. Gọi OpenWeatherMap lấy thời tiết thực tế
        Double temp = 0.0, hum = 0.0, wind = 0.0, lst = 0.0;
        try {
            String weatherUrl = String.format(OPEN_WEATHER_URL, location.getLatitude(), location.getLongitude(), OPEN_WEATHER_API_KEY);
            OpenWeatherResponse weatherRes = restTemplate.getForObject(weatherUrl, OpenWeatherResponse.class);
            if (weatherRes != null && weatherRes.getMain() != null) {
                temp = weatherRes.getMain().getTemp();
                hum = weatherRes.getMain().getHumidity();
                wind = (weatherRes.getWind() != null) ? weatherRes.getWind().getSpeed() : 0.0;
                lst = Math.round((temp + 2.0) * 10.0) / 10.0;
            }
        } catch (Exception e) {
            log.error("Lỗi lấy Weather cho Map Detail: {}", e.getMessage());
        }

        // 3. Gọi Nominatim (Đã sửa lỗi 403 Forbidden)
        String addressDetail = location.getName(); // Mặc định là tên tỉnh nếu lỗi
        try {
            String geoUrl = String.format(NOMINATIM_URL, location.getLatitude(), location.getLongitude());

            // --- SỬA QUAN TRỌNG: Thêm User-Agent giả lập trình duyệt thật ---
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://nominatim.openstreetmap.org/");

            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

            // Dùng exchange thay vì getForObject để gửi được Header
            ResponseEntity<JsonNode> response = restTemplate.exchange(geoUrl, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            if (body != null && body.has("address")) {
                JsonNode addr = body.get("address");
                // Ưu tiên lấy tên Phường/Xã/Đường
                if (addr.has("suburb")) addressDetail = addr.get("suburb").asText() + ", " + location.getName();
                else if (addr.has("village")) addressDetail = addr.get("village").asText() + ", " + location.getName();
                else if (addr.has("town")) addressDetail = addr.get("town").asText() + ", " + location.getName();
                else if (addr.has("road")) addressDetail = addr.get("road").asText() + ", " + location.getName();
            }
        } catch (Exception e) {
            // Nếu lỗi, chỉ log nhẹ và bỏ qua để không làm chậm app
            log.warn("Không lấy được địa chỉ chi tiết (Dùng mặc định): {}", e.getMessage());
        }

        return MapDataResponse.builder()
                .id(location.getId())
                .name(location.getName())
                .lat(location.getLatitude().doubleValue())
                .lon(location.getLongitude().doubleValue())
                .predictedRain(predictedRain)
                .status(status)
                .currentTemp(temp)
                .currentHumidity(hum)
                .currentWind(wind)
                .currentLst(lst)
                .addressDetail(addressDetail)
                .build();
    }

    public PredictionRequest getCurrentWeatherFromApi(Long locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa điểm"));

        String url = String.format(OPEN_WEATHER_URL, location.getLatitude(), location.getLongitude(), OPEN_WEATHER_API_KEY);
        log.info("Goi OpenWeatherMap: {}", url);

        try {
            OpenWeatherResponse response = restTemplate.getForObject(url, OpenWeatherResponse.class);

            if (response != null && response.getMain() != null) {
                PredictionRequest req = new PredictionRequest();
                req.setLocationName(location.getName());

                Double temp = response.getMain().getTemp();
                Double humidity = response.getMain().getHumidity();
                Double windSpeed = (response.getWind() != null) ? response.getWind().getSpeed() : 0.0;

                req.setTemperature(temp);
                req.setHumidity(humidity);
                req.setInputWindSpeed(windSpeed);
                req.setWindUnit("ms");

                req.setLst(Math.round((temp + 2.0) * 10.0) / 10.0);

                return req;
            }
        } catch (Exception e) {
            log.error("Lỗi gọi OpenWeatherMap: ", e);
            throw new RuntimeException("Không lấy được thời tiết: " + e.getMessage());
        }
        return null;
    }

    public List<PredictionResponse> getMultiDayForecast(Long locationId) {
        // 1. Lấy thông tin địa điểm
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa điểm"));
        // 2. Gọi API OpenWeatherMap (Lấy dữ liệu thô 5 ngày)
        String url = String.format(OPEN_WEATHER_FORECAST_URL, location.getLatitude(), location.getLongitude(), OPEN_WEATHER_API_KEY);
        TranHaiViet.rain_forecast_web.dto.OpenWeatherForecastResponse rawData = restTemplate.getForObject(url, TranHaiViet.rain_forecast_web.dto.OpenWeatherForecastResponse.class);

        if (rawData == null || rawData.getList() == null) return List.of();

        List<PredictionResponse> finalResults = new java.util.ArrayList<>();

        // 3. Lấy mưa ngày hôm qua (từ DB) để làm đầu vào cho Ngày 1
        // (Nếu không có thì giả định là 0)
        double previousDayRain = 0.0;
        List<PredictionHistory> history = getHistoryByLocation(locationId);
        if (!history.isEmpty()) {
            previousDayRain = history.get(0).getPredictedRainfall() != null ? history.get(0).getPredictedRainfall() : 0.0;
        }

        // 4. VÒNG LẶP DỰ BÁO ĐỆ QUY (RECURSIVE LOOP)
        for (TranHaiViet.rain_forecast_web.dto.OpenWeatherForecastResponse.ForecastItem item : rawData.getList()) {
            // Chỉ lấy dữ liệu lúc 12:00 trưa mỗi ngày để dự báo
            if (item.getDtTxt().contains("12:00:00")) {

                // A. Chuẩn bị Request gửi sang Python
                PredictionRequest req = new PredictionRequest();
                req.setLocationName(location.getName());
                req.setLat(location.getLatitude().doubleValue());
                req.setLon(location.getLongitude().doubleValue());

                req.setTemperature(item.getMain().getTemp());
                req.setHumidity(item.getMain().getHumidity());
                req.setInputWindSpeed(item.getWind().getSpeed());
                req.setWindUnit("ms");

                // Ước lượng LST
                req.setLst(item.getMain().getTemp() + 2.0);

                // B. Gọi Python
                PredictionResponse res = pythonMLService.getPredictionFromPython(req);

                // Gán ngày dự báo để hiển thị
                res.setMessage(item.getDtTxt().split(" ")[0]); // Lấy ngày YYYY-MM-DD

                // C. Cập nhật kết quả vào danh sách
                finalResults.add(res);

                // D. [Đệ quy] Cập nhật mưa vừa dự báo làm đầu vào cho vòng lặp sau
                previousDayRain = res.getPredictedRainfall();
            }
        }

        return finalResults;
    }

    // 1. Lấy trang danh sách chờ (Pending)
    public Page<PredictionHistory> getPendingHistoriesPaginated(int page, int size) {
        // Sắp xếp ngày dự báo mới nhất lên đầu
        Pageable pageable = PageRequest.of(page, size, Sort.by("predictionTimestamp").descending());
        return predictionHistoryRepository.findAllByActualRainfallIsNull(pageable);
    }

    // 2. Lấy trang lịch sử toàn bộ (History)
    public Page<PredictionHistory> getAllHistoriesPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("predictionTimestamp").descending());
        return predictionHistoryRepository.findAll(pageable);
    }

    // 3. Lấy thống kê số lượng hoàn thành
    public long getCompletedCount() {
        return predictionHistoryRepository.countByActualRainfallIsNotNull();
    }

    // 4. Lấy sai số trung bình
    public double getAverageError() {
        Double avg = predictionHistoryRepository.calculateAverageError();
        return avg != null ? avg : 0.0;
    }

    @Transactional
    public PredictionResponse predictAndSave(Long locationId, PredictionRequest request) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa điểm ID: " + locationId));

        request.setLocationName(location.getName());
        request.setLat(location.getLatitude().doubleValue());
        request.setLon(location.getLongitude().doubleValue());

        log.info("Dữ liệu gửi sang Python: Name={}, Lat={}, Lon={}, Mưa={}, Gió={}",
                location.getName(), request.getLat(), request.getLon(),
                request.getTemperature(), request.getInputWindSpeed());

        PredictionResponse pythonResponse = pythonMLService.getPredictionFromPython(request);

        PredictionHistory history = PredictionHistory.builder()
                .location(location)
                .predictionTimestamp(LocalDateTime.now())
                .predictedForDate(LocalDate.now())
                .predictedRainfall(pythonResponse.getPredictedRainfall())
                .build();

        PredictionHistory savedHistory = predictionHistoryRepository.save(history);

        PredictionFeature feature = PredictionFeature.builder()
                .predictionHistory(savedHistory)
                .inputLst(request.getLst())
                .inputHumidity(request.getHumidity())
                .inputTemp(request.getTemperature())
                .inputWindSpeed(request.getInputWindSpeed())
                .build();

        predictionFeatureRepository.save(feature);

        return pythonResponse;
    }

    // 1. Tìm kiếm History Nâng cao
    public Page<PredictionHistory> searchHistoryAdvanced(
            Long locationId, LocalDate startDate, LocalDate endDate,
            Double minRain, Double maxRain, String status, String keyword,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("predictedForDate").descending());

        // Xử lý keyword rỗng
        if (keyword != null && keyword.trim().isEmpty()) keyword = null;
        if (status != null && status.trim().isEmpty()) status = null;

        return predictionHistoryRepository.searchHistoryAdvanced(
                locationId, startDate, endDate, minRain, maxRain, status, keyword, pageable);
    }

    // 2. Tìm kiếm Verification Nâng cao
    public Page<PredictionHistory> searchPendingAdvanced(
            Long locationId, LocalDate startDate, LocalDate endDate,
            Double minRain, Double maxRain, String keyword,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("predictionTimestamp").descending());
        if (keyword != null && keyword.trim().isEmpty()) keyword = null;

        return predictionHistoryRepository.searchPendingAdvanced(
                locationId, startDate, endDate, minRain, maxRain, keyword, pageable);
    }

    // 1. Tìm kiếm Lịch sử có phân trang
    public Page<PredictionHistory> searchHistoryPaginated(Long locationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("predictedForDate").descending());
        return predictionHistoryRepository.searchHistory(locationId, pageable);
    }

    // 2. Tìm kiếm Pending có phân trang
    public Page<PredictionHistory> searchPendingPaginated(Long locationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("predictionTimestamp").descending());
        return predictionHistoryRepository.searchPending(locationId, pageable);
    }

    // 3. Xóa bản ghi lịch sử
    public void deleteHistory(Long id) {
        predictionHistoryRepository.deleteById(id);
    }

    public List<PredictionHistory> getPendingHistories() {
        return predictionHistoryRepository.findAll().stream()
                .filter(h -> h.getActualRainfall() == null)
                .toList();
    }

    public List<PredictionHistory> getCompletedHistories() {
        return predictionHistoryRepository.findAll().stream()
                .filter(h -> h.getActualRainfall() != null)
                .toList();
    }

    // 1. Đếm tổng số Subscriber
    public long getTotalSubscribers() {
        return subscriberRepository.count();
    }

    // 2. Lấy danh sách Top 5 điểm mưa to nhất ngày mai (Để hiện cảnh báo)
    public List<PredictionHistory> getTopRisksForTomorrow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // Nếu muốn test dữ liệu cũ thì có thể sửa thành LocalDate.now() hoặc ngày khác
        return predictionHistoryRepository.findTop5ByPredictedForDateOrderByPredictedRainfallDesc(tomorrow);
    }

    // Hàm phụ trợ lấy Top Risk cho ngày bất kỳ (nếu cần test)
    public List<PredictionHistory> getTopRisksForDate(LocalDate date) {
        return predictionHistoryRepository.findTop5ByPredictedForDateOrderByPredictedRainfallDesc(date);
    }

    // 3. Lấy dữ liệu phân bố mưa (Cho biểu đồ tròn)
    public List<Long> getRainDistribution() {
        long noRain = predictionHistoryRepository.countNoRain();
        long lightRain = predictionHistoryRepository.countLightRain();
        long moderateRain = predictionHistoryRepository.countModerateRain();
        long heavyRain = predictionHistoryRepository.countHeavyRain();

        // Trả về mảng 4 số: [Không mưa, Mưa nhỏ, Mưa vừa, Mưa to]
        return List.of(noRain, lightRain, moderateRain, heavyRain);
    }

    public List<PredictionHistory> getLatestForecasts() {
        List<Location> locations = locationRepository.findAll();
        List<PredictionHistory> latestList = new ArrayList<>();

        for (Location loc : locations) {
            // Tìm dự báo mới nhất, nếu không có thì bỏ qua hoặc tạo dummy (tùy chọn)
            predictionHistoryRepository.findLatestByLocationId(loc.getId())
                    .ifPresent(latestList::add);
        }
        return latestList;
    }

    @Transactional
    public void updateActualRainfall(Long historyId, Double actualRainfall) {
        PredictionHistory history = predictionHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch sử ID: " + historyId));

        history.setActualRainfall(actualRainfall);
        predictionHistoryRepository.save(history);
    }
}