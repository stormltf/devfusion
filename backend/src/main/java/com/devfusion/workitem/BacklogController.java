package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.iteration.Iteration;
import com.devfusion.iteration.mapper.IterationMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Backlog：未规划工作项列表 + 规划进/移出迭代 */
@RestController
public class BacklogController {

    private final WorkItemMapper itemMapper;
    private final IterationMapper iterationMapper;
    private final WorkItemService service;

    public BacklogController(WorkItemMapper itemMapper, IterationMapper iterationMapper,
                             WorkItemService service) {
        this.itemMapper = itemMapper;
        this.iterationMapper = iterationMapper;
        this.service = service;
    }

    @Data
    public static class PlanReq {
        private List<Long> itemIds;
        private Long iterationId;
    }

    @GetMapping("/api/projects/{projectId}/backlog")
    public Result<PageResult<Map<String, Object>>> backlog(@PathVariable Long projectId,
                                                            @RequestParam(required = false) String type,
                                                            @RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "50") long size) {
        Page<WorkItem> p = itemMapper.selectPage(new Page<>(page, size),
                Wrappers.<WorkItem>lambdaQuery()
                        .eq(WorkItem::getProjectId, projectId)
                        .isNull(WorkItem::getIterationId)
                        .eq(type != null && !type.isBlank(), WorkItem::getType, type)
                        .orderByDesc(WorkItem::getPriority).orderByDesc(WorkItem::getUpdatedAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        service.fill(rows, p.getRecords());
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(rows);
        result.setTotal(p.getTotal());
        result.setPage(p.getCurrent());
        result.setSize(p.getSize());
        return Result.ok(result);
    }

    @PostMapping("/api/projects/{projectId}/backlog/plan")
    @Transactional
    public Result<Void> plan(@PathVariable Long projectId, @RequestBody PlanReq req) {
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new ApiException("itemIds 不能为空");
        }
        if (req.getIterationId() == null) {
            throw new ApiException("iterationId 不能为空");
        }
        Iteration it = iterationMapper.selectById(req.getIterationId());
        if (it == null || !it.getProjectId().equals(projectId)) {
            throw new ApiException("迭代不存在或不属于该项目");
        }
        for (Long id : req.getItemIds()) {
            WorkItem item = itemMapper.selectById(id);
            if (item == null || !item.getProjectId().equals(projectId)) {
                continue;
            }
            item.setIterationId(req.getIterationId());
            itemMapper.updateById(item);
            service.logActivity(item, "PLAN", Map.of("iterationId", req.getIterationId()));
        }
        return Result.ok();
    }

    @PostMapping("/api/projects/{projectId}/backlog/unplan")
    @Transactional
    public Result<Void> unplan(@PathVariable Long projectId, @RequestBody PlanReq req) {
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new ApiException("itemIds 不能为空");
        }
        for (Long id : req.getItemIds()) {
            WorkItem item = itemMapper.selectById(id);
            if (item == null || !item.getProjectId().equals(projectId)) {
                continue;
            }
            itemMapper.update(null, Wrappers.<WorkItem>lambdaUpdate()
                    .eq(WorkItem::getId, id)
                    .set(WorkItem::getIterationId, null));
            item.setIterationId(null);
            service.logActivity(item, "UNPLAN", new HashMap<>());
        }
        return Result.ok();
    }
}
