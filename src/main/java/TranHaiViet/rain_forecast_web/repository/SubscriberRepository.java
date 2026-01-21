package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.Subscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByEmailAndLocationId(String email, Long locationId);
    List<Subscriber> findByLocationId(Long locationId);

    // Tìm kiếm Subscribers
    @Query("SELECT s FROM Subscriber s WHERE " +
            "(:locationId IS NULL OR s.location.id = :locationId) AND " +
            "(:keyword IS NULL OR s.email LIKE %:keyword%)")
    Page<Subscriber> searchSubscribers(
            @Param("locationId") Long locationId,
            @Param("keyword") String keyword,
            Pageable pageable);
}