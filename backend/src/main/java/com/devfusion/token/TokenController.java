package com.devfusion.token;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 个人访问令牌管理（JWT 登录态；明文仅创建时返回一次） */
@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Data
    public static class CreateReq {
        private String name;
    }

    @GetMapping
    public Result<List<AccessToken>> list() {
        return Result.ok(tokenService.listMine(CurrentUser.id()));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateReq req) {
        AccessToken t = tokenService.issue(CurrentUser.id(), req.getName());
        // tokenHash 字段此刻承载的是明文，仅本次返回
        return Result.ok(Map.of("id", t.getId(), "name", t.getName(), "token", t.getTokenHash()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tokenService.revoke(CurrentUser.id(), id);
        return Result.ok();
    }
}
