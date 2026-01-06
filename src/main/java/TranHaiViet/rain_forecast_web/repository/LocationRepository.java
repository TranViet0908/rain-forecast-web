package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    // Tìm địa điểm theo tên (Ví dụ: "Đà Nẵng")
    Optional<Location> findByName(String name);
}