package com.devfusion.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.mapper.WorkItemMapper;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/projects/{projectId}/statuses")
public class WorkflowController {

    private final WorkItemStatusMapper statusMapper;
    private final WorkItemMapper workItemMapper;
    private final WorkflowService workflowService;

    public WorkflowController(WorkItemStatusMapper statusMapper, WorkItemMapper workItemMapper,
                              WorkflowService workflowService) {
        this.statusMapper = statusMapper;
        this.workItemMapper = workItemMapper;
        this.workflowService = workflowService;
    }

    @Data
    public static class StatusReq {
        private String type;
        private String name;
        /** TODO / IN_PROGRESS / DONE */
        private String category;
    }

    @GetMapping
    public Result<List<WorkItemStatus>> list(@PathVariable Long projectId) {
        return Result.ok(workflowService.listByProject(projectId));
    }

    @PostMapping
    @Transactional
    public Result<WorkItemStatus> create(@PathVariable Long projectId, @RequestBody StatusReq req) {
        if (!List.of("STORY", "TASK", "BUG").contains(req.getType())) {
            throw new ApiException("type 必须是 STORY/TASK/BUG");
        }
        if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(req.getCategory())) {
            throw new ApiException("category 必须是 TODO/IN_PROGRESS/DONE");
        }
        int maxSort = statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                        .eq(WorkItemStatus::getProjectId, projectId).eq(WorkItemStatus::getType, req.getType()))
                .stream().mapToInt(WorkItemStatus::getSort).max().orElse(0);
        WorkItemStatus s = new WorkItemStatus();
        s.setProjectId(projectId);
        s.setType(req.getType());
        s.setCode("S" + (maxSort + 1) + "-" + System.currentTimeMillis() % 10000);
        s.setName(req.getName());
        s.setCategory(req.getCategory());
        s.setSort(maxSort + 1);
        s.setIsInitial(0);
        s.setIsFinal("DONE".equals(req.getCategory()) ? 1 : 0);
        statusMapper.insert(s);
        return Result.ok(s);
    }

    @PutMapping("/{sid}")
    public Result<Void> update(@PathVariable Long projectId, @PathVariable Long sid, @RequestBody StatusReq req) {
        WorkItemStatus s = statusMapper.selectById(sid);
        if (s == null || !s.getProjectId().equals(projectId)) {
            throw ApiException.notFound("状态");
        }
        if (req.getName() != null) {
            s.setName(req.getName());
        }
        if (req.getCategory() != null) {
            s.setCategory(req.getCategory());
            s.setIsFinal("DONE".equals(req.getCategory()) ? 1 : 0);
        }
        statusMapper.updateById(s);
        return Result.ok();
    }

    @DeleteMapping("/{sid}")
    @Transactional
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long sid) {
        WorkItemStatus s = statusMapper.selectById(sid);
        if (s == null || !s.getProjectId().equals(projectId)) {
            throw ApiException.notFound("状态");
        }
        if (s.getIsInitial() == 1) {
            throw new ApiException("初始状态不可删除");
        }
        Long inUse = workItemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, projectId)
                .eq(WorkItem::getType, s.getType())
                .eq(WorkItem::getStatusCode, s.getCode()));
        if (inUse > 0) {
            throw new ApiException("有 " + inUse + " 个工作项处于该状态，不可删除");
        }
        statusMapper.deleteById(sid);
        return Result.ok();
    }
}
