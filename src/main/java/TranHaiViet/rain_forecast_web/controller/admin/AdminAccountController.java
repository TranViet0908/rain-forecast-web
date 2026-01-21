package TranHaiViet.rain_forecast_web.controller.admin;

import TranHaiViet.rain_forecast_web.entity.User;
import TranHaiViet.rain_forecast_web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final int PAGE_SIZE = 10;

    // 1. Hiển thị danh sách
    @GetMapping
    public String showAccounts(
            @RequestParam(defaultValue = "0") int page, // Nhận trang hiện tại
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            Model model) {

        if (keyword != null && keyword.trim().isEmpty()) keyword = null;
        if (role != null && role.trim().isEmpty()) role = null;

        // Tạo Pageable
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);

        // Gọi Repository lấy dữ liệu dạng Page
        Page<User> userPage = userRepository.searchUsers(role, keyword, pageable);

        // Truyền Page object xuống View (Lưu ý: tên biến vẫn là 'users' để đỡ sửa nhiều ở View)
        model.addAttribute("users", userPage);

        // Truyền các biến cần thiết cho thanh phân trang và bộ lọc
        model.addAttribute("currentPage", page);
        model.addAttribute("role", role);
        model.addAttribute("keyword", keyword);
        model.addAttribute("pageTitle", "Quản lý Tài khoản");

        return "admin/accounts";
    }

    @PostMapping("/save")
    @ResponseBody
    public Map<String, Object> saveUser(@RequestBody Map<String, String> payload) {
        try {
            String idStr = payload.get("id");
            String username = payload.get("username");
            String fullName = payload.get("fullName");
            String role = payload.get("role");
            String password = payload.get("password");

            User user;
            if (idStr != null && !idStr.isEmpty()) {
                user = userRepository.findById(Long.parseLong(idStr))
                        .orElseThrow(() -> new RuntimeException("User not found"));
                if (password != null && !password.isEmpty()) {
                    user.setPassword(passwordEncoder.encode(password));
                }
            } else {
                if (userRepository.findByUsername(username).isPresent()) {
                    throw new RuntimeException("Tên đăng nhập đã tồn tại!");
                }
                user = new User();
                user.setUsername(username);
                user.setPassword(passwordEncoder.encode(password));
            }
            user.setFullName(fullName);
            user.setRole(role);
            userRepository.save(user);
            return Map.of("success", true, "message", "Lưu thành công!");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/delete")
    @ResponseBody
    public Map<String, Object> deleteUser(@RequestBody Map<String, Long> payload) {
        try {
            Long id = payload.get("id");
            userRepository.findById(id).ifPresent(u -> {
                if ("admin".equals(u.getUsername())) throw new RuntimeException("Không thể xóa Super Admin!");
            });
            userRepository.deleteById(id);
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}