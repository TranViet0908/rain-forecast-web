//package TranHaiViet.rain_forecast_web.service;
//
//import TranHaiViet.rain_forecast_web.entity.User;
//import TranHaiViet.rain_forecast_web.repository.UserRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class DataSeeder implements CommandLineRunner {
//
//    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println("---------------------------------------------");
//        System.out.println(">>> ĐANG KHỞI TẠO DỮ LIỆU ADMIN (DATA SEEDER)");
//
//        // 1. Kiểm tra xem trong DB đang có gì (Debug cho bạn xem)
//        long count = userRepository.count();
//        System.out.println(">>> Số lượng User hiện tại: " + count);
//        userRepository.findAll().forEach(u -> {
//            System.out.println(">>> User tìm thấy: " + u.getUsername() + " | Pass Hash: " + u.getPassword());
//        });
//
//        // 2. XÓA SẠCH LÀM LẠI (Để đảm bảo không còn rác)
//        userRepository.deleteAll();
//        System.out.println(">>> Đã xóa sạch bảng User cũ.");
//
//        // 3. TẠO ADMIN MỚI (Pass: 123456)
//        // Spring sẽ tự mã hóa chuỗi "123456" chuẩn BCrypt
//        User admin = new User();
//        admin.setUsername("admin");
//        admin.setPassword(passwordEncoder.encode("123456"));
//        admin.setFullName("Super Admin (Auto Created)");
//        admin.setRole("ADMIN");
//
//        userRepository.save(admin);
//        System.out.println(">>> Đã tạo User: admin / 123456");
//
//        // 4. TẠO MANAGER MỚI (Pass: 123456)
//        User manager = new User();
//        manager.setUsername("manager");
//        manager.setPassword(passwordEncoder.encode("123456"));
//        manager.setFullName("Manager (Auto Created)");
//        manager.setRole("MANAGER");
//
//        userRepository.save(manager);
//        System.out.println(">>> Đã tạo User: manager / 123456");
//
//        System.out.println("---------------------------------------------");
//    }
//}