package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.PredictionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionHistoryRepository extends JpaRepository<PredictionHistory, Long> {
    List<PredictionHistory> findByLocationIdOrderByPredictedForDateDesc(Long locationId);
    List<PredictionHistory> findByLocationIdOrderByPredictedForDateAsc(Long locationId);
    Optional<PredictionHistory> findByLocationIdAndPredictedForDate(Long locationId, LocalDate predictedForDate);

    Page<PredictionHistory> findAllByActualRainfallIsNull(Pageable pageable);
    Page<PredictionHistory> findAll(Pageable pageable);
    long countByActualRainfallIsNotNull();
    @Query("SELECT AVG(ABS(p.predictedRainfall - p.actualRainfall)) FROM PredictionHistory p WHERE p.actualRainfall IS NOT NULL")
    Double calculateAverageError();

    // 1. Lọc Lịch sử (Có thể lọc theo Location hoặc lấy tất cả)
    @Query("SELECT p FROM PredictionHistory p WHERE (:locationId IS NULL OR p.location.id = :locationId)")
    Page<PredictionHistory> searchHistory(@Param("locationId") Long locationId, Pageable pageable);

    // 2. Lọc Verification (Pending) - Lọc theo Location và đảm bảo Actual là NULL
    @Query("SELECT p FROM PredictionHistory p WHERE p.actualRainfall IS NULL AND (:locationId IS NULL OR p.location.id = :locationId)")
    Page<PredictionHistory> searchPending(@Param("locationId") Long locationId, Pageable pageable);

    // --- MỚI: BỘ LỌC TỔNG HỢP CHO HISTORY ---
    // Hỗ trợ: Khu vực, Ngày (từ-đến), Lượng mưa dự báo (min-max), Trạng thái, Từ khóa
    @Query("SELECT p FROM PredictionHistory p WHERE " +
            "(:locationId IS NULL OR p.location.id = :locationId) AND " +
            "(:startDate IS NULL OR p.predictedForDate >= :startDate) AND " +
            "(:endDate IS NULL OR p.predictedForDate <= :endDate) AND " +
            "(:minRain IS NULL OR p.predictedRainfall >= :minRain) AND " +
            "(:maxRain IS NULL OR p.predictedRainfall <= :maxRain) AND " +
            "(:status IS NULL OR (:status = 'COMPLETED' AND p.actualRainfall IS NOT NULL) OR (:status = 'PENDING' AND p.actualRainfall IS NULL)) AND " +
            "(:keyword IS NULL OR p.location.name LIKE %:keyword%)")
    Page<PredictionHistory> searchHistoryAdvanced(
            @Param("locationId") Long locationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("minRain") Double minRain,
            @Param("maxRain") Double maxRain,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    // --- MỚI: BỘ LỌC TỔNG HỢP CHO VERIFICATION (PENDING) ---
    // Mặc định luôn lọc actualRainfall IS NULL
    @Query("SELECT p FROM PredictionHistory p WHERE p.actualRainfall IS NULL AND " +
            "(:locationId IS NULL OR p.location.id = :locationId) AND " +
            "(:startDate IS NULL OR p.predictedForDate >= :startDate) AND " +
            "(:endDate IS NULL OR p.predictedForDate <= :endDate) AND " +
            "(:minRain IS NULL OR p.predictedRainfall >= :minRain) AND " +
            "(:maxRain IS NULL OR p.predictedRainfall <= :maxRain) AND " +
            "(:keyword IS NULL OR p.location.name LIKE %:keyword%)")
    Page<PredictionHistory> searchPendingAdvanced(
            @Param("locationId") Long locationId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("minRain") Double minRain,
            @Param("maxRain") Double maxRain,
            @Param("keyword") String keyword,
            Pageable pageable);
    // 1. Lấy Top 5 nơi dự báo mưa lớn nhất trong 1 ngày cụ thể (cho ngày mai)
    List<PredictionHistory> findTop5ByPredictedForDateOrderByPredictedRainfallDesc(LocalDate date);

    // 2. Các hàm đếm phân loại mưa (cho biểu đồ tròn)
    // Không mưa (0 - 0.1mm)
    @Query("SELECT COUNT(p) FROM PredictionHistory p WHERE p.predictedRainfall < 0.1")
    long countNoRain();

    // Mưa nhỏ (0.1 - 16mm)
    @Query("SELECT COUNT(p) FROM PredictionHistory p WHERE p.predictedRainfall >= 0.1 AND p.predictedRainfall < 16")
    long countLightRain();

    // Mưa vừa (16 - 50mm)
    @Query("SELECT COUNT(p) FROM PredictionHistory p WHERE p.predictedRainfall >= 16 AND p.predictedRainfall < 50")
    long countModerateRain();

    // Mưa to (> 50mm)
    @Query("SELECT COUNT(p) FROM PredictionHistory p WHERE p.predictedRainfall >= 50")
    long countHeavyRain();

    @Query("SELECT p FROM PredictionHistory p WHERE p.location.id = :locationId ORDER BY p.predictionTimestamp DESC LIMIT 1")
    Optional<PredictionHistory> findLatestByLocationId(@Param("locationId") Long locationId);
}