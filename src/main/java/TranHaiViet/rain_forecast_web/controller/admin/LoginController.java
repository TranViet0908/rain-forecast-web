package TranHaiViet.rain_forecast_web.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // URL trên trình duyệt sẽ là: http://localhost:8080/admin/login
    @GetMapping("/admin/login")
    public String showLoginPage() {
        // Trỏ đúng vào file: templates/admin/auth/login.html
        return "admin/auth/login";
    }
}