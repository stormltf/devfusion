package com.devfusion.deploy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.artifact.Artifact;
import com.devfusion.artifact.mapper.ArtifactMapper;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.environment.Environment;
import com.devfusion.environment.mapper.EnvironmentMapper;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.devfusion.deploy.mapper.DeploymentMapper;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentMapper mapper;
    private final ArtifactMapper artifactMapper;
    private final ApplicationMapper applicationMapper;
    private final EnvironmentMapper environmentMapper;
    private final DeployExecutor executor;
    private final com.devfusion.notification.NotificationService notificationService;
    private final com.devfusion.user.mapper.UserMapper userMapper;
    private final StringRedisTemplate redis;

    public DeploymentController(DeploymentMapper mapper, ArtifactMapper artifactMapper,
                                ApplicationMapper applicationMapper, EnvironmentMapper environmentMapper,
                                DeployExecutor executor,
                                com.devfusion.notification.NotificationService notificationService,
                                com.devfusion.user.mapper.UserMapper userMapper,
                                StringRedisTemplate redis) {
        this.mapper = mapper;
        this.artifactMapper = artifactMapper;
        this.applicationMapper = applicationMapper;
        this.environmentMapper = environmentMapper;
        this.executor = executor;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
        this.redis = redis;
    }

    @Data
    public static class DeployReq {
        private Long applicationId;
        private Long artifactId;
        private Long environmentId;
        private Long deployTemplateId;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Long applicationId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        Page<Deployment> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<Deployment>lambdaQuery()
                        .eq(applicationId != null, Deployment::getApplicationId, applicationId)
                        .eq(status != null && !status.isBlank(), Deployment::getStatus, status)
                        .orderByDesc(Deployment::getId));
        List<Map<String, Object>> rows = p.getRecords().stream().map(this::toVo).toList();
        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setRecords(rows);
        pr.setTotal(p.getTotal());
        pr.setPage(p.getCurrent());
        pr.setSize(p.getSize());
        return Result.ok(pr);
    }

    @PostMapping
    public Result<Deployment> create(@RequestBody DeployReq req) {
        Application app = applicationMapper.selectById(req.getApplicationId());
        if (app == null) {
            throw ApiException.notFound("应用");
        }
        Artifact artifact = artifactMapper.selectById(req.getArtifactId());
        if (artifact == null || !Objects.equals(artifact.getApplicationId(), req.getApplicationId())) {
            throw new ApiException("制品不存在或不属于该应用");
        }
        Environment env = environmentMapper.selectById(req.getEnvironmentId());
        if (env == null) {
            throw ApiException.notFound("环境");
        }
        Deployment d = new Deployment();
        d.setApplicationId(app.getId());
        d.setArtifactId(artifact.getId());
        d.setEnvironmentId(env.getId());
        d.setVersion(artifact.getVersion());
        d.setDeployType("DEPLOY");
        d.setDeployTemplateId(req.getDeployTemplateId());
        d.setOperatorId(CurrentUser.id());
        mapper.insert(d);
        prepare(env, d);
        return Result.ok(d);
    }

    /** 根据环境审批开关决定：直接执行或挂起待审批 */
    private void prepare(Environment env, Deployment d) {
        boolean needApproval = env.getRequireApproval() != null && env.getRequireApproval() == 1;
        if (needApproval) {
            d.setStatus("PENDING_APPROVAL");
            mapper.updateById(d);
            notificationService.sendToEach(adminIds(), "APPROVAL_PENDING",
                    "部署单待审批", "部署单 #" + d.getId() + " 到环境「" + env.getName() + "」等待审批",
                    "/deployments");
        } else {
            d.setStatus("PENDING");
            mapper.updateById(d);
            executor.submitAsync(d.getId());
        }
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        Deployment d = getOrThrow(id);
        if (!"PENDING_APPROVAL".equals(d.getStatus())) {
            throw new ApiException("该部署单不在待审批状态");
        }
        d.setStatus("PENDING");
        mapper.updateById(d);
        executor.submitAsync(d.getId());
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        Deployment d = getOrThrow(id);
        if (!"PENDING_APPROVAL".equals(d.getStatus())) {
            throw new ApiException("该部署单不在待审批状态");
        }
        d.setStatus("REJECTED");
        d.setFinishedAt(java.time.LocalDateTime.now());
        mapper.updateById(d);
        return Result.ok();
    }

    private java.util.List<Long> adminIds() {
        return userMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.devfusion.user.User>lambdaQuery()
                        .eq(com.devfusion.user.User::getRole, "ADMIN"))
                .stream().map(com.devfusion.user.User::getId).toList();
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Deployment d = getOrThrow(id);
        return Result.ok(toVo(d));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        Deployment d = getOrThrow(id);
        if (!"PENDING".equals(d.getStatus()) && !"DEPLOYING".equals(d.getStatus())
                && !"PENDING_APPROVAL".equals(d.getStatus())) {
            throw new ApiException("仅排队中、待审批或部署中的部署单可以取消");
        }
        if ("PENDING_APPROVAL".equals(d.getStatus())) {
            d.setStatus("CANCELED");
            d.setFinishedAt(java.time.LocalDateTime.now());
            mapper.updateById(d);
            return Result.ok();
        }
        redis.opsForValue().set(DeployExecutor.CANCEL_KEY_PREFIX + id, "1", java.time.Duration.ofHours(1));
        return Result.ok();
    }

    /** 回滚 = 把该应用该环境上一次成功部署的制品重新发一个回滚部署单（参考简单云实践） */
    @PostMapping("/{id}/rollback")
    public Result<Deployment> rollback(@PathVariable Long id) {
        Deployment origin = getOrThrow(id);
        if (!"SUCCESS".equals(origin.getStatus())) {
            throw new ApiException("仅成功的部署可以回滚");
        }
        Deployment last = mapper.selectOne(Wrappers.<Deployment>lambdaQuery()
                .eq(Deployment::getApplicationId, origin.getApplicationId())
                .eq(Deployment::getEnvironmentId, origin.getEnvironmentId())
                .eq(Deployment::getStatus, "SUCCESS")
                .lt(Deployment::getId, origin.getId())
                .ne(Deployment::getVersion, origin.getVersion())
                .orderByDesc(Deployment::getId)
                .last("LIMIT 1"));
        if (last == null) {
            throw new ApiException("没有更早的成功版本可回滚");
        }
        Deployment d = new Deployment();
        d.setApplicationId(origin.getApplicationId());
        d.setArtifactId(last.getArtifactId());
        d.setEnvironmentId(origin.getEnvironmentId());
        d.setVersion(last.getVersion());
        d.setDeployType("ROLLBACK");
        d.setRollbackOfId(origin.getId());
        d.setOperatorId(CurrentUser.id());
        mapper.insert(d);
        prepare(environmentMapper.selectById(d.getEnvironmentId()), d);
        return Result.ok(d);
    }

    private Deployment getOrThrow(Long id) {
        Deployment d = mapper.selectById(id);
        if (d == null) {
            throw ApiException.notFound("部署单");
        }
        return d;
    }

    private Map<String, Object> toVo(Deployment d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("applicationId", d.getApplicationId());
        m.put("artifactId", d.getArtifactId());
        m.put("environmentId", d.getEnvironmentId());
        m.put("version", d.getVersion());
        m.put("deployType", d.getDeployType());
        m.put("rollbackOfId", d.getRollbackOfId());
        m.put("deployTemplateId", d.getDeployTemplateId());
        m.put("status", d.getStatus());
        m.put("operatorId", d.getOperatorId());
        m.put("log", d.getLog());
        m.put("startedAt", d.getStartedAt());
        m.put("finishedAt", d.getFinishedAt());
        m.put("createdAt", d.getCreatedAt());
        Application app = applicationMapper.selectById(d.getApplicationId());
        m.put("applicationName", app != null ? app.getName() : null);
        Environment env = environmentMapper.selectById(d.getEnvironmentId());
        m.put("environmentName", env != null ? env.getName() : null);
        return m;
    }
}
