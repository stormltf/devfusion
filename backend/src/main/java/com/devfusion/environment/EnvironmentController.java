package com.devfusion.environment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.environment.mapper.EnvironmentMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentMapper mapper;

    public EnvironmentController(EnvironmentMapper mapper) {
        this.mapper = mapper;
    }

    @Data
    public static class EnvReq {
        @NotBlank
        private String key;
        @NotBlank
        private String name;
        private String type;
        private String description;
        private String hosts;
        private Integer requireApproval;
        private Long hostGroupId;
    }

    @GetMapping
    public Result<List<Environment>> list() {
        return Result.ok(mapper.selectList(Wrappers.<Environment>lambdaQuery()
                .orderByAsc(Environment::getSort).orderByAsc(Environment::getId)));
    }

    @PostMapping
    public Result<Environment> create(@RequestBody EnvReq req) {
        Long count = mapper.selectCount(Wrappers.<Environment>lambdaQuery().eq(Environment::getKey, req.getKey()));
        if (count > 0) {
            throw new ApiException("环境标识已存在");
        }
        Environment env = new Environment();
        env.setKey(req.getKey().toUpperCase());
        env.setName(req.getName());
        env.setType(req.getType() == null ? "TEST" : req.getType());
        env.setDescription(req.getDescription());
        env.setHosts(req.getHosts());
        if (req.getRequireApproval() != null) {
            env.setRequireApproval(req.getRequireApproval());
        }
        if (req.getHostGroupId() != null) {
            env.setHostGroupId(req.getHostGroupId());
        }
        env.setSort(0);
        mapper.insert(env);
        return Result.ok(env);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody EnvReq req) {
        Environment env = getOrThrow(id);
        if (req.getName() != null) {
            env.setName(req.getName());
        }
        if (req.getType() != null) {
            env.setType(req.getType());
        }
        if (req.getDescription() != null) {
            env.setDescription(req.getDescription());
        }
        if (req.getHosts() != null) {
            env.setHosts(req.getHosts());
        }
        if (req.getRequireApproval() != null) {
            env.setRequireApproval(req.getRequireApproval());
        }
        if (req.getHostGroupId() != null) {
            env.setHostGroupId(req.getHostGroupId());
        }
        mapper.updateById(env);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        mapper.deleteById(id);
        return Result.ok();
    }

    private Environment getOrThrow(Long id) {
        Environment env = mapper.selectById(id);
        if (env == null) {
            throw ApiException.notFound("环境");
        }
        return env;
    }
}
