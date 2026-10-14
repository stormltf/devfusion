package com.devfusion.deploy;

import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.artifact.Artifact;
import com.devfusion.artifact.mapper.ArtifactMapper;
import com.devfusion.environment.Environment;
import com.devfusion.environment.mapper.EnvironmentMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.devfusion.deploy.mapper.DeploymentMapper;

/**
 * 部署执行器：参考简单云主机部署四步（下载制品→前置脚本→替换内容→后置脚本+健康检查），模拟执行。
 */
@Slf4j
@Component
public class DeployExecutor {

    public static final String CANCEL_KEY_PREFIX = "df:cancel:deploy:";

    private final DeploymentMapper deploymentMapper;
    private final ApplicationMapper applicationMapper;
    private final ArtifactMapper artifactMapper;
    private final EnvironmentMapper environmentMapper;
    private final com.devfusion.deploy.mapper.DeployTemplateMapper templateMapper;
    private final com.devfusion.host.mapper.HostMapper hostMapper;
    private final com.devfusion.notification.NotificationService notificationService;
    private final StringRedisTemplate redis;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public DeployExecutor(DeploymentMapper deploymentMapper, ApplicationMapper applicationMapper,
                          ArtifactMapper artifactMapper, EnvironmentMapper environmentMapper,
                          com.devfusion.deploy.mapper.DeployTemplateMapper templateMapper,
                          com.devfusion.host.mapper.HostMapper hostMapper,
                          com.devfusion.notification.NotificationService notificationService,
                          StringRedisTemplate redis) {
        this.deploymentMapper = deploymentMapper;
        this.applicationMapper = applicationMapper;
        this.artifactMapper = artifactMapper;
        this.environmentMapper = environmentMapper;
        this.templateMapper = templateMapper;
        this.hostMapper = hostMapper;
        this.notificationService = notificationService;
        this.redis = redis;
    }

    public void submitAsync(Long deploymentId) {
        pool.submit(() -> {
            try {
                execute(deploymentId);
            } catch (Exception e) {
                log.error("deployment {} crashed", deploymentId, e);
                Deployment d = deploymentMapper.selectById(deploymentId);
                if (d != null && "DEPLOYING".equals(d.getStatus())) {
                    d.setStatus("FAILED");
                    d.setFinishedAt(LocalDateTime.now());
                    deploymentMapper.updateById(d);
                }
            }
        });
    }

    private void execute(Long id) {
        Deployment d = deploymentMapper.selectById(id);
        if (d == null) {
            return;
        }
        d.setStatus("DEPLOYING");
        d.setStartedAt(LocalDateTime.now());
        d.setLog("");
        deploymentMapper.updateById(d);
        redis.delete(CANCEL_KEY_PREFIX + id);

        Application app = applicationMapper.selectById(d.getApplicationId());
        Artifact artifact = artifactMapper.selectById(d.getArtifactId());
        Environment env = environmentMapper.selectById(d.getEnvironmentId());

        List<String> header = new java.util.ArrayList<>(List.of(
                "[time] " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        + " 部署单 #" + id + " 开始执行"
                        + ("ROLLBACK".equals(d.getDeployType()) ? "（回滚）" : ""),
                "[target] 应用: " + (app != null ? app.getName() : d.getApplicationId())
                        + "  环境: " + (env != null ? env.getName() : d.getEnvironmentId())
                        + "  版本: " + d.getVersion(),
                "[step 1/4] 从制品库下载制品 " + (artifact != null ? artifact.getPath() : "-"),
                "[step 2/4] 执行前置脚本（备份当前版本、停应用）",
                "[step 3/4] 替换部署内容并启动",
                "[step 4/4] 执行后置脚本，健康检查 /actuator/health"));

        // 部署模板：体现前置/后置脚本
        if (d.getDeployTemplateId() != null) {
            com.devfusion.deploy.DeployTemplate tpl = templateMapper.selectById(d.getDeployTemplateId());
            if (tpl != null) {
                header.add("[template] 使用部署模板: " + tpl.getName());
                if (tpl.getPreScript() != null && !tpl.getPreScript().isBlank()) {
                    header.add("[template] 前置脚本 >>> " + tpl.getPreScript());
                }
                if (tpl.getPostScript() != null && !tpl.getPostScript().isBlank()) {
                    header.add("[template] 后置脚本 >>> " + tpl.getPostScript());
                }
                if (tpl.getHealthCheckUrl() != null && !tpl.getHealthCheckUrl().isBlank()) {
                    header.add("[template] 健康检查地址: " + tpl.getHealthCheckUrl());
                }
            }
        }
        // 主机组：按主机逐台输出
        if (env != null && env.getHostGroupId() != null) {
            java.util.List<com.devfusion.host.Host> hosts = hostMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.devfusion.host.Host>lambdaQuery()
                            .eq(com.devfusion.host.Host::getGroupId, env.getHostGroupId()));
            for (com.devfusion.host.Host h : hosts) {
                header.add("[host] " + h.getName() + " (" + h.getIp() + ") 状态=" + h.getStatus());
            }
        }

        StringBuilder sb = new StringBuilder();
        try {
            for (String line : header) {
                if (canceled(id)) {
                    finish(d, sb, "CANCELED");
                    return;
                }
                sb.append(line).append("\n");
                d.setLog(sb.toString());
                deploymentMapper.updateById(d);
                Thread.sleep(700);
            }
            boolean fail = false;
            if (artifact == null) {
                sb.append("[error] 制品不存在，部署中止\n");
                fail = true;
            } else {
                sb.append("[health] HTTP 200 {\"status\":\"UP\"}\n");
                sb.append("[done] 部署成功，当前运行版本 ").append(d.getVersion()).append("\n");
            }
            d.setLog(sb.toString());
            finish(d, sb, fail ? "FAILED" : "SUCCESS");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finish(d, sb, "FAILED");
        }
    }

    private void finish(Deployment d, StringBuilder sb, String status) {
        d.setStatus(status);
        d.setFinishedAt(LocalDateTime.now());
        d.setLog(sb.toString());
        deploymentMapper.updateById(d);
        if (d.getOperatorId() != null) {
            notificationService.send(d.getOperatorId(), "DEPLOY_RESULT",
                    "部署" + ("SUCCESS".equals(status) ? "成功" : "失败"),
                    "部署单 #" + d.getId() + " 版本 " + d.getVersion() + " 结果: " + status,
                    "/deployments");
        }
    }

    private boolean canceled(Long id) {
        Boolean exists = redis.hasKey(CANCEL_KEY_PREFIX + id);
        return exists != null && exists;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
