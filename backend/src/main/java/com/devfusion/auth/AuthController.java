package com.devfusion.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.auth.JwtUtil.Token;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginReq req) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.username()));
        if (user == null || !encoder.matches(req.password(), user.getPassword())) {
            throw new ApiException(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new ApiException(403, "账号已被禁用");
        }
        Token token = jwtUtil.issue(user.getId());
        redis.opsForValue().set(AuthInterceptor.SESSION_KEY_PREFIX + token.jti(),
                String.valueOf(user.getId()), Duration.ofMillis(token.ttlMillis()));
        user.setPassword(null);
        return Result.ok(Map.of("token", token.value(), "user", user));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String auth) {
        try {
            ClaimsHolder holder = jtiOf(auth);
            redis.delete(AuthInterceptor.SESSION_KEY_PREFIX + holder.jti());
        } catch (Exception ignored) {
            // token 已无效，登出视为成功
        }
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<User> me() {
        Long userId = com.devfusion.common.CurrentUser.id();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(401, "用户不存在");
        }
        user.setPassword(null);
        return Result.ok(user);
    }

    private record ClaimsHolder(String jti) {
    }

    private ClaimsHolder jtiOf(String auth) {
        String token = auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        return new ClaimsHolder(jwtUtil.parse(token).getId());
    }
}
