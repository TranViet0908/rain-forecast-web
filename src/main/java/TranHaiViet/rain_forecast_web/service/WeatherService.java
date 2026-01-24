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
import java.util.stream.Collectors;

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
                .orElseThrow(() -> new RuntimeException("Location not found"));

        // 1. Lấy dữ liệu Realtime (Giống trang Index)
        Double temp = 0.0, hum = 0.0, wind = 0.0, lst = 0.0;
        Double predictedRain = 0.0;

        try {
            PredictionRequest weatherNow = getCurrentWeatherFromApi(locationId);
            if (weatherNow != null) {
                // Lấy thông số môi trường thực tế
                temp = weatherNow.getTemperature();
                hum = weatherNow.getHumidity();
                wind = weatherNow.getInputWindSpeed();
                lst = weatherNow.getLst();

                // Chạy AI dự báo ngay lập tức (saveToDb = false)
                PredictionResponse aiResponse = predictRainfall(locationId, weatherNow, false);
                predictedRain = aiResponse.getPredictedRainfall();
            }
        } catch (Exception e) {
            log.error("Lỗi lấy dữ liệu Map Detail: {}", e.getMessage());
        }

        // 2. Xác định trạng thái
        String status = "SAFE";
        if (predictedRain > 50) status = "DANGER";
        else if (predictedRain > 10) status = "WARNING";

        // 3. Lấy địa chỉ (Giữ nguyên logic cũ)
        String addressDetail = location.getName();
        // ... (Code lấy địa chỉ Nominatim giữ nguyên hoặc bỏ qua nếu không cần thiết) ...

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
        Location location = locationRepository.findById(locationId).orElseThrow();
        String url = String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%s&lon=%s&appid=%s&units=metric",
                location.getLatitude(), location.getLongitude(), "5796abbde9106b7da4febfae8c44c232");

        var rawData = restTemplate.getForObject(url, TranHaiViet.rain_forecast_web.dto.OpenWeatherForecastResponse.class);
        List<PredictionResponse> results = new ArrayList<>();

        if (rawData != null && rawData.getList() != null) {
            for (var item : rawData.getList()) {
                // Lấy khung giờ 12:00 trưa
                if (item.getDtTxt().contains("12:00")) {
                    PredictionRequest req = new PredictionRequest();
                    req.setLocationName(location.getName());
                    req.setLat(location.getLatitude().doubleValue());
                    req.setLon(location.getLongitude().doubleValue());

                    // Lấy input đầu vào của ngày đó
                    double t = item.getMain().getTemp();
                    double h = item.getMain().getHumidity();
                    double w = item.getWind().getSpeed();

                    req.setTemperature(t);
                    req.setHumidity(h);
                    req.setInputWindSpeed(w);
                    req.setWindUnit("ms");
                    req.setLst(t + 2.0); // Giả lập LST từ nhiệt độ KK

                    // Gọi Python
                    PredictionResponse res = pythonMLService.getPredictionFromPython(req);

                    // [QUAN TRỌNG] Gói thêm dữ liệu vào message: Date|Temp|Hum|Wind
                    String date = item.getDtTxt().split(" ")[0];
                    res.setMessage(String.format("%s|%.1f|%.0f|%.1f", date, t, h, w));

                    results.add(res);
                }
            }
        }
        return results;
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
    // [QUAN TRỌNG] Đổi tên hàm và thêm tham số saveToDb
    public PredictionResponse predictRainfall(Long locationId, PredictionRequest request, boolean saveToDb) {

        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy địa điểm ID: " + locationId));

        // Gán thông tin địa lý để gửi sang Python
        request.setLocationName(location.getName());
        request.setLat(location.getLatitude().doubleValue());
        request.setLon(location.getLongitude().doubleValue());

        // 1. Gọi Python lấy kết quả dự báo
        PredictionResponse pythonResponse = pythonMLService.getPredictionFromPython(request);

        // 2. LOGIC QUYẾT ĐỊNH LƯU HAY KHÔNG
        if (saveToDb) {
            log.info("💾 Đang lưu kết quả dự báo vào DB cho: {}", location.getName());

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
        } else {
            log.info("🧪 Chế độ Mô phỏng: KHÔNG lưu vào DB ({})", location.getName());
        }

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

    public List<PredictionHistory> getRealtimeForecastForAll() {
        List<Location> locations = locationRepository.findAll();

        // Sử dụng Parallel Stream để xử lý song song 11 tỉnh (Tăng tốc độ load trang)
        return locations.parallelStream().map(loc -> {
                    try {
                        // 1. Lấy thời tiết hiện tại (API OpenWeather)
                        PredictionRequest weatherNow = getCurrentWeatherFromApi(loc.getId());

                        // Nếu API lỗi thì trả về null hoặc data mặc định
                        if (weatherNow == null) return null;

                        // 2. Dự báo AI (Tham số false = KHÔNG LƯU DB)
                        PredictionResponse forecast = predictRainfall(loc.getId(), weatherNow, false);

                        // 3. Tạo đối tượng giả lập (Mock Entity) để View (index.html) hiển thị được
                        // Vì index.html đang mong đợi object kiểu PredictionHistory
                        PredictionHistory historyMock = new PredictionHistory();
                        historyMock.setLocation(loc);
                        historyMock.setPredictedRainfall(forecast.getPredictedRainfall());

                        // Set các thông số môi trường để hiển thị Badge nhiệt độ
                        PredictionFeature featureMock = PredictionFeature.builder()
                                .inputTemp(weatherNow.getTemperature()) // Nhiệt độ thật
                                .inputLst(weatherNow.getLst())
                                .inputHumidity(weatherNow.getHumidity())
                                .build();

                        historyMock.setPredictionFeature(featureMock);

                        return historyMock;

                    } catch (Exception e) {
                        log.error("Lỗi lấy data realtime cho {}: {}", loc.getName(), e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull) // Loại bỏ các tỉnh bị lỗi
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateActualRainfall(Long historyId, Double actualRainfall) {
        PredictionHistory history = predictionHistoryRepository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch sử ID: " + historyId));

        history.setActualRainfall(actualRainfall);
        predictionHistoryRepository.save(history);
    }
}