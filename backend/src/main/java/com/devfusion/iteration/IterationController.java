package com.devfusion.iteration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.WorkItemActivity;
import com.devfusion.workitem.mapper.WorkItemActivityMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import com.devfusion.workflow.WorkItemStatus;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.devfusion.iteration.mapper.IterationMapper;

@RestController
public class IterationController {

    private final IterationMapper iterationMapper;
    private final WorkItemMapper workItemMapper;
    private final WorkItemStatusMapper statusMapper;
    private final WorkItemActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IterationController(IterationMapper iterationMapper, WorkItemMapper workItemMapper,
                               WorkItemStatusMapper statusMapper, WorkItemActivityMapper activityMapper,
                               UserMapper userMapper) {
        this.iterationMapper = iterationMapper;
        this.workItemMapper = workItemMapper;
        this.statusMapper = statusMapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
    }

    @Data
    public static class IterationReq {
        @NotBlank
        private String name;
        private String goal;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Data
    public static class StatusReq {
        private String status;
    }

    @GetMapping("/api/projects/{projectId}/iterations")
    public Result<List<Map<String, Object>>> list(@PathVariable Long projectId,
                                                  @RequestParam(required = false) String status) {
        List<Iteration> iterations = iterationMapper.selectList(Wrappers.<Iteration>lambdaQuery()
                .eq(Iteration::getProjectId, projectId)
                .eq(status != null && !status.isBlank(), Iteration::getStatus, status)
                .orderByDesc(Iteration::getId));
        List<Map<String, Object>> result = iterations.stream().map(it -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", it.getId());
            m.put("projectId", it.getProjectId());
            m.put("name", it.getName());
            m.put("goal", it.getGoal());
            m.put("status", it.getStatus());
            m.put("startDate", it.getStartDate());
            m.put("endDate", it.getEndDate());
            Long total = workItemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                    .eq(WorkItem::getIterationId, it.getId()));
            Long done = workItemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                    .eq(WorkItem::getIterationId, it.getId())
                    .in(WorkItem::getStatusCode, doneStatusCodes(projectId)));
            m.put("totalCount", total);
            m.put("doneCount", done);
            return m;
        }).toList();
        return Result.ok(result);
    }

    @PostMapping("/api/projects/{projectId}/iterations")
    public Result<Iteration> create(@PathVariable Long projectId, @RequestBody IterationReq req) {
        Iteration it = new Iteration();
        it.setProjectId(projectId);
        it.setName(req.getName());
        it.setGoal(req.getGoal());
        it.setStartDate(req.getStartDate());
        it.setEndDate(req.getEndDate());
        it.setStatus("PLANNED");
        it.setCreatedBy(CurrentUser.id());
        iterationMapper.insert(it);
        return Result.ok(it);
    }

    @GetMapping("/api/iterations/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Iteration it = getOrThrow(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", it.getId());
        m.put("projectId", it.getProjectId());
        m.put("name", it.getName());
        m.put("goal", it.getGoal());
        m.put("status", it.getStatus());
        m.put("startDate", it.getStartDate());
        m.put("endDate", it.getEndDate());
        User owner = it.getCreatedBy() != null ? userMapper.selectById(it.getCreatedBy()) : null;
        m.put("ownerId", it.getCreatedBy());
        m.put("ownerName", owner != null ? owner.getNickname() : null);

        List<WorkItem> items = workItemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getIterationId, id).orderByDesc(WorkItem::getUpdatedAt));
        Map<String, String> categoryByCode = categoryByCode(it.getProjectId());
        long todo = 0, inProgress = 0, done = 0;
        double spSum = 0;
        for (WorkItem w : items) {
            spSum += w.getStoryPoint() == null ? 0 : w.getStoryPoint();
            String cat = categoryByCode.get(w.getStatusCode());
            if ("DONE".equals(cat)) {
                done++;
            } else if ("IN_PROGRESS".equals(cat)) {
                inProgress++;
            } else {
                todo++;
            }
        }
        m.put("items", items);
        m.put("todoCount", todo);
        m.put("inProgressCount", inProgress);
        m.put("doneCount", done);
        m.put("totalCount", items.size());
        m.put("progress", items.isEmpty() ? 0.0 : Math.round(done * 1000.0 / items.size()) / 1000.0);
        m.put("storyPointSum", spSum);
        m.put("recommendedCapacity", recommendedCapacity(it));
        return Result.ok(m);
    }

    /** 燃尽图：按日返回剩余未完成数（工作项数 / story point）+ 理想线，数据来自状态流转记录 */
    @GetMapping("/api/iterations/{id}/burndown")
    public Result<Map<String, Object>> burndown(@PathVariable Long id,
                                                @RequestParam(defaultValue = "ITEM") String metric) {
        Iteration it = getOrThrow(id);
        List<WorkItem> items = workItemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getIterationId, id));
        Map<String, String> categoryByCode = categoryByCode(it.getProjectId());
        Set<String> doneCodes = categoryByCode.entrySet().stream()
                .filter(e -> "DONE".equals(e.getValue())).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());

        // 每个工作项首次进入终态的时间（取自状态流转 activity）
        Map<Long, LocalDateTime> doneAt = new HashMap<>();
        List<WorkItemActivity> acts = activityMapper.selectList(Wrappers.<WorkItemActivity>lambdaQuery()
                .in(WorkItemActivity::getItemId, items.stream().map(WorkItem::getId).toList()));
        for (WorkItemActivity a : acts) {
            if (!"STATUS".equals(a.getAction())) {
                continue;
            }
            String to = parseTo(a.getDetail());
            if (to != null && doneCodes.contains(to)) {
                doneAt.merge(a.getItemId(), a.getCreatedAt(), (x, y) -> x.isBefore(y) ? x : y);
            }
        }

        double initial = "STORY_POINT".equals(metric)
                ? items.stream().mapToDouble(w -> w.getStoryPoint() == null ? 0 : w.getStoryPoint()).sum()
                : items.size();

        List<Map<String, Object>> points = new ArrayList<>();
        LocalDate start = it.getStartDate();
        LocalDate end = it.getEndDate();
        if (start != null && end != null && !end.isBefore(start)) {
            long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
            LocalDate last = "COMPLETED".equals(it.getStatus()) ? end : (end.isBefore(LocalDate.now()) ? end : LocalDate.now());
            for (LocalDate d = start; !d.isAfter(last); d = d.plusDays(1)) {
                LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();
                double doneValue = "STORY_POINT".equals(metric)
                        ? items.stream().filter(w -> doneAt.get(w.getId()) != null && doneAt.get(w.getId()).isBefore(dayEnd))
                            .mapToDouble(w -> w.getStoryPoint() == null ? 0 : w.getStoryPoint()).sum()
                        : items.stream().filter(w -> doneAt.get(w.getId()) != null && doneAt.get(w.getId()).isBefore(dayEnd)).count();
                double actual = Math.max(initial - doneValue, 0);
                double ideal = initial * (1 - (double) (ChronoUnit.DAYS.between(start, d) + 1) / totalDays);
                Map<String, Object> p = new HashMap<>();
                p.put("date", d.toString());
                p.put("ideal", Math.round(ideal * 100.0) / 100.0);
                p.put("actual", Math.round(actual * 100.0) / 100.0);
                points.add(p);
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("metric", metric);
        m.put("initial", initial);
        m.put("points", points);
        return Result.ok(m);
    }

    /** 推荐容量：已完成迭代日均速度 × 当前迭代天数；无历史完成迭代返回 null */
    private Double recommendedCapacity(Iteration it) {
        if (it.getStartDate() == null || it.getEndDate() == null || it.getEndDate().isBefore(it.getStartDate())) {
            return null;
        }
        List<Iteration> doneIters = iterationMapper.selectList(Wrappers.<Iteration>lambdaQuery()
                .eq(Iteration::getProjectId, it.getProjectId())
                .eq(Iteration::getStatus, "COMPLETED"));
        if (doneIters.isEmpty()) {
            return null;
        }
        List<WorkItem> all = workItemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, it.getProjectId()));
        double dailySpeedSum = 0;
        int counted = 0;
        for (Iteration d : doneIters) {
            if (d.getStartDate() == null || d.getEndDate() == null || d.getEndDate().isBefore(d.getStartDate())) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(d.getStartDate(), d.getEndDate()) + 1;
            double sp = all.stream().filter(w -> d.getId().equals(w.getIterationId()))
                    .mapToDouble(w -> w.getStoryPoint() == null ? 0 : w.getStoryPoint()).sum();
            dailySpeedSum += sp / days;
            counted++;
        }
        if (counted == 0) {
            return null;
        }
        double avgDaily = dailySpeedSum / counted;
        long curDays = ChronoUnit.DAYS.between(it.getStartDate(), it.getEndDate()) + 1;
        return Math.round(avgDaily * curDays * 100.0) / 100.0;
    }

    private Map<String, String> categoryByCode(Long projectId) {
        return statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                        .eq(WorkItemStatus::getProjectId, projectId)).stream()
                .collect(HashMap::new, (m, s) -> m.put(s.getCode(), s.getCategory()), HashMap::putAll);
    }

    private String parseTo(String detail) {
        if (detail == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detail);
            JsonNode to = node.get("to");
            return to == null ? null : to.asText();
        } catch (Exception e) {
            return null;
        }
    }

    @PutMapping("/api/iterations/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody IterationReq req) {
        Iteration it = getOrThrow(id);
        it.setName(req.getName());
        it.setGoal(req.getGoal());
        it.setStartDate(req.getStartDate());
        it.setEndDate(req.getEndDate());
        iterationMapper.updateById(it);
        return Result.ok();
    }

    /** 状态机：PLANNED→IN_PROGRESS→COMPLETED；PLANNED/IN_PROGRESS→CANCELED */
    @PutMapping("/api/iterations/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        Iteration it = getOrThrow(id);
        String from = it.getStatus();
        String to = req.getStatus();
        Set<List<String>> allowed = Set.of(
                List.of("PLANNED", "IN_PROGRESS"),
                List.of("IN_PROGRESS", "COMPLETED"),
                List.of("PLANNED", "CANCELED"),
                List.of("IN_PROGRESS", "CANCELED"));
        if (!allowed.contains(List.of(from, to))) {
            throw new ApiException("迭代不允许从 " + from + " 流转到 " + to);
        }
        if ("COMPLETED".equals(to)) {
            Long undone = workItemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                    .eq(WorkItem::getIterationId, id)
                    .notIn(WorkItem::getStatusCode, doneStatusCodes(it.getProjectId())));
            if (undone > 0) {
                throw new ApiException("还有 " + undone + " 个工作项未完成，不能完成迭代");
            }
        }
        it.setStatus(to);
        iterationMapper.updateById(it);
        return Result.ok();
    }

    @DeleteMapping("/api/iterations/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        Iteration it = getOrThrow(id);
        Long items = workItemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getIterationId, id));
        if (items > 0) {
            throw new ApiException("迭代内有 " + items + " 个工作项，请先移出");
        }
        iterationMapper.deleteById(it.getId());
        return Result.ok();
    }

    private List<String> doneStatusCodes(Long projectId) {
        return statusMapper.selectList(Wrappers.<com.devfusion.workflow.WorkItemStatus>lambdaQuery()
                        .eq(com.devfusion.workflow.WorkItemStatus::getProjectId, projectId)
                        .eq(com.devfusion.workflow.WorkItemStatus::getCategory, "DONE"))
                .stream().map(com.devfusion.workflow.WorkItemStatus::getCode).toList();
    }

    private Iteration getOrThrow(Long id) {
        Iteration it = iterationMapper.selectById(id);
        if (it == null) {
            throw ApiException.notFound("迭代");
        }
        return it;
    }
}
