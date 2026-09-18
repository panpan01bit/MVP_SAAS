package com.toolfix.controller;

import com.toolfix.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 演示登录：单账号（配置文件注入），Session 保持登录态。
 * 仅用于本地 Demo 展示，生产应替换为完整认证体系。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    @Value("${toolfix.security.demo-admin-email}")
    private String demoEmail;

    @Value("${toolfix.security.demo-admin-password}")
    private String demoPassword;

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (!demoEmail.equalsIgnoreCase(request.getEmail()) || !demoPassword.equals(request.getPassword())) {
            log.warn("登录失败: {}", request.getEmail());
            return ApiResponse.error("邮箱或密码错误");
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("adminEmail", demoEmail);
        session.setMaxInactiveInterval(8 * 3600); // 8 小时

        log.info("演示账号登录成功: {}", demoEmail);
        return ApiResponse.success("登录成功", Map.of("email", demoEmail));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success("已退出", null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminEmail") == null) {
            return ApiResponse.success("未登录", Map.of("email", ""));
        }
        return ApiResponse.success(Map.of("email", (String) session.getAttribute("adminEmail")));
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }
}
