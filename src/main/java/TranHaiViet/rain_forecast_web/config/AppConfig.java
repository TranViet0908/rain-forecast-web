package TranHaiViet.rain_forecast_web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    // Tạo Bean RestTemplate để dùng chung cho cả project
    // Giúp thực hiện các HTTP Request (GET, POST)
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}