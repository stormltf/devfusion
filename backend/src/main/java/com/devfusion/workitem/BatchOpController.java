package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.project.ProjectMember;
import com.devfusion.project.mapper.ProjectMemberMapper;
import com.devfusion.workflow.WorkItemStatus;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 批量改状态/指派人/优先级/迭代 + 批量删除，逐条写 activity 留痕 */
@RestController
public class BatchOpController {

    private static final Set<String> FIELDS = Set.of("statusCode", "assigneeId", "priority", "iterationId");

    private final WorkItemMapper itemMapper;
    private final WorkItemStatusMapper statusMapper;
    private final ProjectMemberMapper memberMapper;
    private final WorkItemService service;

    public BatchOpController(WorkItemMapper itemMapper, WorkItemStatusMapper statusMapper,
                              ProjectMemberMapper memberMapper, WorkItemService service) {
        this.itemMapper = itemMapper;
        this.statusMapper = statusMapper;
        this.memberMapper = memberMapper;
        this.service = service;
    }

    @Data
    public static class BatchUpdateReq {
        private List<Long> itemIds;
        private String field;
        private Object value;
    }

    @Data
    public static class BatchDeleteReq {
        private List<Long> itemIds;
    }

    @PostMapping("/api/projects/{projectId}/items/batch-update")
    @Transactional
    public Result<Void> batchUpdate(@PathVariable Long projectId, @RequestBody BatchUpdateReq req) {
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new ApiException("itemIds 不能为空");
        }
        if (!FIELDS.contains(req.getField())) {
            throw new ApiException("field 必须是 " + FIELDS);
        }
        for (Long id : req.getItemIds()) {
            WorkItem item = itemMapper.selectById(id);
            if (item == null || !item.getProjectId().equals(projectId)) {
                continue;
            }
            switch (req.getField()) {
                case "statusCode" -> applyStatus(projectId, item, String.valueOf(req.getValue()));
                case "assigneeId" -> applyAssignee(projectId, item, req.getValue());
                case "priority" -> {
                    item.setPriority(String.valueOf(req.getValue()));
                    itemMapper.updateById(item);
                    service.logActivity(item, "UPDATE", Map.of("priority", req.getValue()));
                }
                case "iterationId" -> {
                    item.setIterationId(req.getValue() == null ? null : Long.valueOf(String.valueOf(req.getValue())));
                    itemMapper.updateById(item);
                    service.logActivity(item, "UPDATE", Map.of("iterationId", item.getIterationId()));
                }
                default -> throw new ApiException("不支持的字段");
            }
        }
        return Result.ok();
    }

    @PostMapping("/api/projects/{projectId}/items/batch-delete")
    @Transactional
    public Result<Void> batchDelete(@PathVariable Long projectId, @RequestBody BatchDeleteReq req) {
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            throw new ApiException("itemIds 不能为空");
        }
        for (Long id : req.getItemIds()) {
            WorkItem item = itemMapper.selectById(id);
            if (item == null || !item.getProjectId().equals(projectId)) {
                continue;
            }
            service.logActivity(item, "DELETE", Map.of("title", item.getTitle()));
            itemMapper.deleteById(id);
        }
        return Result.ok();
    }

    private void applyStatus(Long projectId, WorkItem item, String code) {
        Long count = statusMapper.selectCount(Wrappers.<WorkItemStatus>lambdaQuery()
                .eq(WorkItemStatus::getProjectId, projectId)
                .eq(WorkItemStatus::getType, item.getType())
                .eq(WorkItemStatus::getCode, code));
        if (count == 0) {
            throw new ApiException("状态 " + code + " 在该项目工作流中不存在");
        }
        String from = item.getStatusCode();
        item.setStatusCode(code);
        itemMapper.updateById(item);
        service.logActivity(item, "STATUS", Map.of("from", from, "to", code));
    }

    private void applyAssignee(Long projectId, WorkItem item, Object value) {
        Long userId = value == null ? null : Long.valueOf(String.valueOf(value));
        if (userId != null) {
            Long count = memberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                    .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
            if (count == 0) {
                throw new ApiException("指派对象必须是项目成员");
            }
        }
        item.setAssigneeId(userId);
        itemMapper.updateById(item);
        service.logActivity(item, "ASSIGN", Map.of("assigneeId", userId));
    }
}
