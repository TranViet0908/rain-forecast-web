package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionHistoryRepository extends JpaRepository<PredictionHistory, Long> {

    // 1. Lấy danh sách lịch sử của một địa điểm cụ thể, sắp xếp ngày giảm dần (Mới nhất lên trên)
    List<PredictionHistory> findByLocationIdOrderByPredictedForDateDesc(Long locationId);

    // 2. Lấy danh sách lịch sử của một địa điểm, sắp xếp ngày tăng dần (Cũ -> Mới)
    List<PredictionHistory> findByLocationIdOrderByPredictedForDateAsc(Long locationId);

    // 3. Tìm bản ghi dự báo cụ thể dựa trên ID địa điểm và Ngày dự báo
    Optional<PredictionHistory> findByLocationIdAndPredictedForDate(Long locationId, LocalDate predictedForDate);
}