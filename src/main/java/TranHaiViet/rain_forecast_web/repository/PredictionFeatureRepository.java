package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.PredictionFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PredictionFeatureRepository extends JpaRepository<PredictionFeature, Long> {

    // Tìm các tham số đầu vào dựa trên ID của lịch sử dự báo
    Optional<PredictionFeature> findByPredictionHistoryId(Long historyId);
}