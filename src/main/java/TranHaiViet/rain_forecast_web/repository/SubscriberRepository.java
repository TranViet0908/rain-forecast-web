package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {
    Optional<Subscriber> findByEmailAndLocationId(String email, Long locationId);
    List<Subscriber> findByLocationId(Long locationId);
}