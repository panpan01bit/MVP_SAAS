package com.toolfix.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolfix.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端接口鉴权拦截器：未登录返回 401 JSON。
 * 保护 /sessions /shops /products /manuals /admin /diagnosis/create-session，
 * H5 客户端诊断接口（HMAC 保护）保持公开。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // CORS 预检放行
        }

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("adminEmail") != null) {
            return true;
        }

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("未登录或会话已过期，请重新登录")));
        return false;
    }
}
