package TranHaiViet.rain_forecast_web.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    // Hàm này sẽ tự động chạy cho mọi Request
    // Nó lấy đường dẫn hiện tại (ví dụ: /, /history, /admin) và gửi xuống HTML
    // với tên biến là "currentUri"
    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}