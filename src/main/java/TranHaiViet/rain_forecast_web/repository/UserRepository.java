package TranHaiViet.rain_forecast_web.repository;

import TranHaiViet.rain_forecast_web.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    // Tìm kiếm theo Role và Từ khóa (username hoặc fullname)
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.fullName LIKE %:keyword%)")
    List<User> searchUsers(@Param("role") String role, @Param("keyword") String keyword);
    // [CẬP NHẬT] Thêm Pageable và trả về Page<User>
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.role = :role) AND " +
            "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.fullName LIKE %:keyword%)")
    Page<User> searchUsers(@Param("role") String role, @Param("keyword") String keyword, Pageable pageable);
}