package com.devfusion.deploy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.Result;
import com.devfusion.deploy.mapper.DeployTemplateMapper;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部署模板 */
@RestController
@RequestMapping("/api/deploy-templates")
public class DeployTemplateController {

    private final DeployTemplateMapper mapper;

    public DeployTemplateController(DeployTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Data
    public static class TplReq {
        private Long appId;
        private String name;
        private String preScript;
        private String postScript;
        private String healthCheckUrl;
    }

    @GetMapping
    public Result<List<DeployTemplate>> list(@RequestParam Long appId) {
        return Result.ok(mapper.selectList(Wrappers.<DeployTemplate>lambdaQuery()
                .eq(DeployTemplate::getAppId, appId).orderByDesc(DeployTemplate::getId)));
    }

    @PostMapping
    public Result<DeployTemplate> create(@RequestBody TplReq req) {
        DeployTemplate t = new DeployTemplate();
        t.setAppId(req.getAppId());
        t.setName(req.getName());
        t.setPreScript(req.getPreScript());
        t.setPostScript(req.getPostScript());
        t.setHealthCheckUrl(req.getHealthCheckUrl());
        mapper.insert(t);
        return Result.ok(t);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }
}
