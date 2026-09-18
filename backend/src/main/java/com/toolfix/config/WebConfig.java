package com.toolfix.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/sessions/**",
                        "/shops/**",
                        "/products/**",
                        "/manuals/**",
                        "/admin/**",
                        "/diagnosis/create-session"
                )
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/me",
                        "/auth/logout",
                        "/diagnosis/*/validate",
                        "/diagnosis/*/chat",
                        "/diagnosis/*/feedback",
                        "/diagnosis/*/resolve",
                        "/diagnosis/demo-session"
                );
    }
}
