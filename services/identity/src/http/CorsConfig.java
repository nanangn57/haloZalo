package http;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableConfigurationProperties(CorsSettings.class)
public class CorsConfig {
    @Bean
    FilterRegistrationBean<CorsFilter> browserCors(CorsSettings settings) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(
            filter(settings.getAllowedOriginPatterns())
        );
        // SessionFilter answers 401 before the controller. Without this header the browser reports that as CORS.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static CorsFilter filter(List<String> allowedOriginPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
