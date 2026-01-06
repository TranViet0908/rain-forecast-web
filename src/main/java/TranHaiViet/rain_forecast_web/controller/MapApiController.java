package TranHaiViet.rain_forecast_web.controller;

import TranHaiViet.rain_forecast_web.dto.MapDataResponse;
import TranHaiViet.rain_forecast_web.entity.Location;
import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import TranHaiViet.rain_forecast_web.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapApiController {

    private final WeatherService weatherService;

    // API lấy danh sách các điểm (Chỉ dữ liệu cơ bản để vẽ map cho nhanh)
    @GetMapping("/locations")
    public ResponseEntity<List<MapDataResponse>> getMapData() {
        List<Location> locations = weatherService.getAllLocations();
        List<MapDataResponse> response = new ArrayList<>();

        for (Location loc : locations) {
            List<PredictionHistory> histories = weatherService.getHistoryByLocation(loc.getId());
            Double rain = 0.0;
            if (!histories.isEmpty()) {
                rain = histories.get(0).getPredictedRainfall();
            }

            String status = "SAFE";
            if (rain > 50) status = "DANGER";
            else if (rain > 10) status = "WARNING";

            response.add(MapDataResponse.builder()
                    .id(loc.getId())
                    .name(loc.getName())
                    .lat(loc.getLatitude().doubleValue())
                    .lon(loc.getLongitude().doubleValue())
                    .predictedRain(rain)
                    .status(status)
                    .build());
        }
        return ResponseEntity.ok(response);
    }

    // --- MỚI: API Lấy chi tiết khi click vào marker (Lazy Load) ---
    @GetMapping("/detail/{id}")
    public ResponseEntity<MapDataResponse> getMapDetail(@PathVariable Long id) {
        MapDataResponse detail = weatherService.getMapLocationDetail(id);
        return ResponseEntity.ok(detail);
    }
}