package com.devfusion.dashboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.app.Application;
import com.devfusion.common.Result;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.deploy.Deployment;
import com.devfusion.deploy.mapper.DeploymentMapper;
import com.devfusion.iteration.Iteration;
import com.devfusion.iteration.mapper.IterationMapper;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.runner.PipelineRun;
import com.devfusion.runner.mapper.PipelineRunMapper;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workflow.WorkItemStatus;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.WorkItemActivity;
import com.devfusion.workitem.mapper.WorkItemActivityMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ApplicationMapper applicationMapper;
    private final PipelineRunMapper runMapper;
    private final DeploymentMapper deploymentMapper;
    private final WorkItemMapper itemMapper;
    private final IterationMapper iterationMapper;
    private final WorkItemStatusMapper statusMapper;
    private final WorkItemActivityMapper activityMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardController(UserMapper userMapper, ProjectMapper projectMapper,
                               ApplicationMapper applicationMapper, PipelineRunMapper runMapper,
                               DeploymentMapper deploymentMapper, WorkItemMapper itemMapper,
                               IterationMapper iterationMapper, WorkItemStatusMapper statusMapper,
                               WorkItemActivityMapper activityMapper) {
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.applicationMapper = applicationMapper;
        this.runMapper = runMapper;
        this.deploymentMapper = deploymentMapper;
        this.itemMapper = itemMapper;
        this.iterationMapper = iterationMapper;
        this.statusMapper = statusMapper;
        this.activityMapper = activityMapper;
    }

    /** 全局总览 */
    @GetMapping("/global")
    public Result<Map<String, Object>> global() {
        Map<String, Object> m = new HashMap<>();
        m.put("userCount", userMapper.selectCount(null));
        m.put("projectCount", projectMapper.selectCount(null));
        m.put("applicationCount", applicationMapper.selectCount(null));
        m.put("runCount", runMapper.selectCount(null));
        m.put("runSuccessCount", runMapper.selectCount(Wrappers.<PipelineRun>lambdaQuery()
                .eq(PipelineRun::getStatus, "SUCCESS")));
        m.put("deploymentCount", deploymentMapper.selectCount(null));
        Long depSuccess = deploymentMapper.selectCount(Wrappers.<Deployment>lambdaQuery()
                .eq(Deployment::getStatus, "SUCCESS"));
        m.put("deploymentSuccessCount", depSuccess);

        // 最近 7 天每日构建次数
        List<Map<String, Object>> runTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = runMapper.selectCount(Wrappers.<PipelineRun>lambdaQuery()
                    .between(PipelineRun::getCreatedAt, day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
            runTrend.add(Map.of("date", day.toString(), "count", count));
        }
        m.put("runTrend", runTrend);

        // 最近 5 条部署
        List<Deployment> recent = deploymentMapper.selectList(Wrappers.<Deployment>lambdaQuery()
                .orderByDesc(Deployment::getId).last("LIMIT 5"));
        m.put("recentDeployments", recent.stream().map(d -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", d.getId());
            r.put("version", d.getVersion());
            r.put("status", d.getStatus());
            r.put("deployType", d.getDeployType());
            r.put("createdAt", d.getCreatedAt());
            Application app = applicationMapper.selectById(d.getApplicationId());
            r.put("applicationName", app != null ? app.getName() : null);
            return r;
        }).toList());
        return Result.ok(m);
    }

    /** 项目维度统计：工作项分布、迭代进度、测试统计 */
    @GetMapping("/project/{projectId}")
    public Result<Map<String, Object>> project(@PathVariable Long projectId) {
        Map<String, Object> m = new HashMap<>();
        List<WorkItem> items = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, projectId));

        // 类型 x 状态分布
        Map<String, Map<String, Long>> byType = new HashMap<>();
        for (String type : List.of("STORY", "TASK", "BUG")) {
            List<WorkItem> typed = items.stream().filter(i -> type.equals(i.getType())).toList();
            Map<String, Long> byStatus = typed.stream().collect(
                    Collectors.groupingBy(WorkItem::getStatusCode, Collectors.counting()));
            byType.put(type, byStatus);
            m.put(type.toLowerCase() + "Count", typed.size());
        }
        m.put("itemsByType", byType);

        Map<String, Long> byPriority = items.stream().collect(
                Collectors.groupingBy(WorkItem::getPriority, Collectors.counting()));
        m.put("itemsByPriority", byPriority);

        // 迭代进度
        List<Iteration> iterations = iterationMapper.selectList(Wrappers.<Iteration>lambdaQuery()
                .eq(Iteration::getProjectId, projectId).orderByDesc(Iteration::getId).last("LIMIT 5"));
        List<String> doneCodes = statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                        .eq(WorkItemStatus::getProjectId, projectId)
                        .eq(WorkItemStatus::getCategory, "DONE"))
                .stream().map(WorkItemStatus::getCode).toList();
        List<Map<String, Object>> iterStats = iterations.stream().map(it -> {
            List<WorkItem> iterItems = items.stream()
                    .filter(i -> it.getId().equals(i.getIterationId())).toList();
            long done = iterItems.stream().filter(i -> doneCodes.contains(i.getStatusCode())).count();
            Map<String, Object> s = new HashMap<>();
            s.put("id", it.getId());
            s.put("name", it.getName());
            s.put("status", it.getStatus());
            s.put("total", iterItems.size());
            s.put("done", done);
            return s;
        }).toList();
        m.put("iterations", iterStats);

        Long myItems = itemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, projectId)
                .eq(WorkItem::getAssigneeId, userId()));
        m.put("myItemCount", myItems);
        m.put("totalItemCount", items.size());
        return Result.ok(m);
    }

    /** 缺陷分析报表：新建 vs 已解决趋势、未解决平均年龄、按优先级/负责人分布、重开次数 */
    @GetMapping("/project/{projectId}/bug-report")
    public Result<Map<String, Object>> bugReport(@PathVariable Long projectId) {
        List<WorkItem> bugs = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getType, "BUG"));

        Map<String, String> categoryByCode = statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                        .eq(WorkItemStatus::getProjectId, projectId)).stream()
                .collect(Collectors.toMap(WorkItemStatus::getCode, WorkItemStatus::getCategory, (a, b) -> a));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            long created = bugs.stream()
                    .filter(b -> !b.getCreatedAt().isBefore(start) && b.getCreatedAt().isBefore(end)).count();
            List<WorkItemActivity> acts = activityMapper.selectList(Wrappers.<WorkItemActivity>lambdaQuery()
                    .in(WorkItemActivity::getItemId, bugs.stream().map(WorkItem::getId).toList())
                    .eq(WorkItemActivity::getAction, "STATUS")
                    .between(WorkItemActivity::getCreatedAt, start, end));
            long resolvedDone = acts.stream().filter(a -> "DONE".equals(categoryByCode.get(parseTo(a.getDetail())))).count();
            Map<String, Object> p = new HashMap<>();
            p.put("date", day.toString());
            p.put("created", created);
            p.put("resolved", resolvedDone);
            trend.add(p);
        }

        // 未解决缺陷平均年龄（天）
        List<WorkItem> unresolved = bugs.stream()
                .filter(b -> !"DONE".equals(categoryByCode.get(b.getStatusCode()))).toList();
        double avgAge = unresolved.isEmpty() ? 0.0 : unresolved.stream()
                .mapToLong(b -> b.getCreatedAt().toLocalDate().until(today).getDays()).average().orElse(0);

        Map<String, Long> byPriority = bugs.stream().collect(
                Collectors.groupingBy(b -> b.getPriority() == null ? "UNKNOWN" : b.getPriority(), Collectors.counting()));

        Map<String, Long> byAssignee = bugs.stream()
                .filter(b -> b.getAssigneeId() != null)
                .collect(Collectors.groupingBy(b -> String.valueOf(b.getAssigneeId()), Collectors.counting()));

        // 重开：状态从 DONE 类回到非 DONE 类计一次
        List<WorkItemActivity> all = activityMapper.selectList(Wrappers.<WorkItemActivity>lambdaQuery()
                .in(WorkItemActivity::getItemId, bugs.stream().map(WorkItem::getId).toList())
                .eq(WorkItemActivity::getAction, "STATUS"));
        long reopen = all.stream().filter(a -> {
            String from = parseFrom(a.getDetail());
            String to = parseTo(a.getDetail());
            return from != null && to != null
                    && "DONE".equals(categoryByCode.get(from))
                    && !"DONE".equals(categoryByCode.get(to));
        }).count();

        Map<String, Object> m = new HashMap<>();
        m.put("trend", trend);
        m.put("unresolvedCount", unresolved.size());
        m.put("avgAgeDays", Math.round(avgAge * 100.0) / 100.0);
        m.put("byPriority", byPriority);
        m.put("byAssignee", byAssignee);
        m.put("reopenCount", reopen);
        return Result.ok(m);
    }

    private String parseTo(String detail) {
        return parseField(detail, "to");
    }

    private String parseFrom(String detail) {
        return parseField(detail, "from");
    }

    private String parseField(String detail, String field) {
        if (detail == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detail);
            JsonNode v = node.get(field);
            return v == null ? null : v.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private Long userId() {
        return com.devfusion.common.CurrentUser.id();
    }

    /**
     * 效能度量（口径）：
     * - 部署频率：窗口内 DEPLOY 类型部署单数（按日）
     * - 部署成功率：SUCCESS 部署单 / 全部终态部署单
     * - 平均构建时长：终态 run 的 durationMs 均值
     * - 构建成功率：SUCCESS run / 全部终态 run
     * - 变更失败率：ROLLBACK 部署单 / 全部部署单（回滚占比）
     * - 平均恢复时长：同一应用+环境一次 FAILED 部署到其后第一次 SUCCESS 部署的间隔均值
     */
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics(@RequestParam(defaultValue = "7") int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);
        List<PipelineRun> runs = runMapper.selectList(Wrappers.<PipelineRun>lambdaQuery()
                .ge(PipelineRun::getCreatedAt, from.atStartOfDay()));
        List<Deployment> deps = deploymentMapper.selectList(Wrappers.<Deployment>lambdaQuery()
                .ge(Deployment::getCreatedAt, from.atStartOfDay()));

        long runTotal = runs.size();
        long runSuccess = runs.stream().filter(r -> "SUCCESS".equals(r.getStatus())).count();
        long runFailed = runs.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long runCanceled = runs.stream().filter(r -> "CANCELED".equals(r.getStatus())).count();
        double avgDuration = runs.stream().filter(r -> r.getDurationMs() != null)
                .mapToLong(PipelineRun::getDurationMs).average().orElse(0);

        long depTotal = deps.size();
        long depSuccess = deps.stream().filter(d -> "SUCCESS".equals(d.getStatus())).count();
        long depRollback = deps.stream().filter(d -> "ROLLBACK".equals(d.getDeployType())).count();

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            long depDay = deps.stream().filter(d -> inRange(d.getCreatedAt(), dayStart, dayEnd)).count();
            long depOkDay = deps.stream().filter(d -> inRange(d.getCreatedAt(), dayStart, dayEnd)
                    && "SUCCESS".equals(d.getStatus())).count();
            long runDay = runs.stream().filter(r -> inRange(r.getCreatedAt(), dayStart, dayEnd)).count();
            long runOkDay = runs.stream().filter(r -> inRange(r.getCreatedAt(), dayStart, dayEnd)
                    && "SUCCESS".equals(r.getStatus())).count();
            Map<String, Object> row = new HashMap<>();
            row.put("date", day.toString());
            row.put("deployCount", depDay);
            row.put("deploySuccessCount", depOkDay);
            row.put("runCount", runDay);
            row.put("runSuccessCount", runOkDay);
            trend.add(row);
        }

        Map<String, Object> m = new HashMap<>();
        m.put("deployFrequency", depTotal);
        m.put("deploySuccessRate", depTotal == 0 ? 0 : round(depSuccess * 100.0 / depTotal));
        m.put("avgBuildDurationMs", Math.round(avgDuration));
        m.put("buildSuccessRate", runTotal == 0 ? 0 : round(runSuccess * 100.0 / runTotal));
        m.put("changeFailRate", depTotal == 0 ? 0 : round(depRollback * 100.0 / depTotal));
        m.put("avgRecoveryMinutes", avgRecoveryMinutes(deps));
        m.put("runFailed", runFailed);
        m.put("runCanceled", runCanceled);
        m.put("trend", trend);
        return Result.ok(m);
    }

    private boolean inRange(LocalDateTime t, LocalDateTime s, LocalDateTime e) {
        return t != null && !t.isBefore(s) && t.isBefore(e);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double avgRecoveryMinutes(List<Deployment> deps) {
        Map<String, List<Deployment>> groups = new HashMap<>();
        for (Deployment d : deps) {
            String key = d.getApplicationId() + ":" + d.getEnvironmentId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<Long> gaps = new ArrayList<>();
        for (List<Deployment> list : groups.values()) {
            list.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            for (int i = 0; i < list.size(); i++) {
                Deployment f = list.get(i);
                if (!"FAILED".equals(f.getStatus()) || f.getFinishedAt() == null) {
                    continue;
                }
                for (int j = i + 1; j < list.size(); j++) {
                    Deployment s = list.get(j);
                    if ("SUCCESS".equals(s.getStatus()) && s.getStartedAt() != null) {
                        gaps.add(java.time.Duration.between(f.getFinishedAt(), s.getStartedAt()).toMinutes());
                        break;
                    }
                }
            }
        }
        if (gaps.isEmpty()) {
            return 0;
        }
        return Math.round(gaps.stream().mapToLong(Long::longValue).average().orElse(0) * 10.0) / 10.0;
    }
}
