package com.devfusion.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.devfusion.app.mapper.ApplicationMapper;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationMapper mapper;
    private final UserMapper userMapper;

    public ApplicationController(ApplicationMapper mapper, UserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    @Data
    public static class AppReq {
        @NotBlank
        private String key;
        @NotBlank
        private String name;
        private String description;
        private String repoUrl;
        private String language;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        List<Application> apps = mapper.selectList(Wrappers.<Application>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Application::getName, keyword)
                .orderByDesc(Application::getUpdatedAt));
        return Result.ok(apps.stream().map(this::toVo).toList());
    }

    @PostMapping
    public Result<Application> create(@RequestBody AppReq req) {
        Long count = mapper.selectCount(Wrappers.<Application>lambdaQuery().eq(Application::getKey, req.getKey()));
        if (count > 0) {
            throw new ApiException("应用标识已存在");
        }
        Application app = new Application();
        app.setKey(req.getKey().toUpperCase());
        app.setName(req.getName());
        app.setDescription(req.getDescription());
        app.setRepoUrl(req.getRepoUrl());
        app.setLanguage(req.getLanguage() == null ? "OTHER" : req.getLanguage());
        app.setOwnerId(CurrentUser.id());
        mapper.insert(app);
        return Result.ok(app);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(toVo(getOrThrow(id)));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AppReq req) {
        Application app = getOrThrow(id);
        if (req.getName() != null) {
            app.setName(req.getName());
        }
        if (req.getDescription() != null) {
            app.setDescription(req.getDescription());
        }
        if (req.getRepoUrl() != null) {
            app.setRepoUrl(req.getRepoUrl());
        }
        if (req.getLanguage() != null) {
            app.setLanguage(req.getLanguage());
        }
        mapper.updateById(app);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        mapper.deleteById(id);
        return Result.ok();
    }

    private Application getOrThrow(Long id) {
        Application app = mapper.selectById(id);
        if (app == null) {
            throw ApiException.notFound("应用");
        }
        return app;
    }

    private Map<String, Object> toVo(Application app) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", app.getId());
        m.put("key", app.getKey());
        m.put("name", app.getName());
        m.put("description", app.getDescription());
        m.put("repoUrl", app.getRepoUrl());
        m.put("language", app.getLanguage());
        m.put("createdAt", app.getCreatedAt());
        m.put("updatedAt", app.getUpdatedAt());
        User owner = userMapper.selectById(app.getOwnerId());
        m.put("ownerName", owner != null ? owner.getNickname() : null);
        return m;
    }
}
