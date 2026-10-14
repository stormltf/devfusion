package com.devfusion.auth;

import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY_PREFIX = "df:session:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public AuthInterceptor(JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new ApiException(401, "未登录");
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(auth.substring(7));
        } catch (Exception e) {
            throw new ApiException(401, "登录已过期，请重新登录");
        }
        String jti = claims.getId();
        Boolean alive = redis.hasKey(SESSION_KEY_PREFIX + jti);
        if (alive == null || !alive) {
            throw new ApiException(401, "会话已失效，请重新登录");
        }
        CurrentUser.set(Long.valueOf(claims.getSubject()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUser.clear();
    }
}
