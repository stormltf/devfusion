package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workflow.WorkItemStatus;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import com.devfusion.workitem.mapper.WorkItemActivityMapper;
import com.devfusion.workitem.mapper.WorkItemCommentMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class WorkItemController {

    private final WorkItemMapper itemMapper;
    private final WorkItemCommentMapper commentMapper;
    private final WorkItemActivityMapper activityMapper;
    private final WorkItemStatusMapper statusMapper;
    private final UserMapper userMapper;
    private final WorkItemService service;

    public WorkItemController(WorkItemMapper itemMapper, WorkItemCommentMapper commentMapper,
                              WorkItemActivityMapper activityMapper, WorkItemStatusMapper statusMapper,
                              UserMapper userMapper, WorkItemService service) {
        this.itemMapper = itemMapper;
        this.commentMapper = commentMapper;
        this.activityMapper = activityMapper;
        this.statusMapper = statusMapper;
        this.userMapper = userMapper;
        this.service = service;
    }

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "updatedAt", "updated_at",
            "createdAt", "created_at",
            "priority", "priority",
            "storyPoint", "story_point",
            "itemNo", "item_no",
            "title", "title",
            "statusCode", "status_code");

    @Data
    public static class ItemReq {
        private String type;
        @NotBlank
        private String title;
        private String description;
        private String statusCode;
        private String priority;
        private Long assigneeId;
        private Long iterationId;
        private Long parentId;
        private Double storyPoint;
        private Double estimateHours;
        private Long releaseId;
        private java.time.LocalDate planStartDate;
        private java.time.LocalDate planEndDate;
    }

    @Data
    public static class TransitionReq {
        @NotBlank
        private String statusCode;
    }

    @Data
    public static class CommentReq {
        @NotBlank
        private String content;
    }

    @GetMapping("/api/projects/{projectId}/items")
    public Result<PageResult<Map<String, Object>>> list(@PathVariable Long projectId,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) String statusCode,
                                                @RequestParam(required = false) String priority,
                                                @RequestParam(required = false) Long assigneeId,
                                                @RequestParam(required = false) Long iterationId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "desc") String direction) {
        String orderColumn = sort == null || sort.isBlank() ? "updated_at" : SORT_COLUMNS.get(sort);
        if (orderColumn == null) {
            throw new ApiException("不支持的排序字段: " + sort);
        }
        boolean asc = "asc".equalsIgnoreCase(direction);
        Page<WorkItem> p = itemMapper.selectPage(new Page<>(page, size),
                Wrappers.<WorkItem>query()
                        .eq("project_id", projectId)
                        .eq(type != null && !type.isBlank(), "type", type)
                        .eq(statusCode != null && !statusCode.isBlank(), "status_code", statusCode)
                        .eq(priority != null && !priority.isBlank(), "priority", priority)
                        .eq(assigneeId != null, "assignee_id", assigneeId)
                        .eq(iterationId != null, "iteration_id", iterationId)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like("title", keyword).or().like("item_no", keyword))
                        .orderBy(true, asc, orderColumn));
        List<Map<String, Object>> rows = new ArrayList<>();
        service.fill(rows, p.getRecords());
        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(rows);
        result.setTotal(p.getTotal());
        result.setPage(p.getCurrent());
        result.setSize(p.getSize());
        return Result.ok(result);
    }

    @PostMapping("/api/projects/{projectId}/items")
    @Transactional
    public Result<WorkItem> create(@PathVariable Long projectId, @RequestBody ItemReq req) {
        WorkItem item = new WorkItem();
        item.setProjectId(projectId);
        item.setType(req.getType());
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setStatusCode(req.getStatusCode());
        item.setPriority(req.getPriority());
        item.setAssigneeId(req.getAssigneeId());
        item.setIterationId(req.getIterationId());
        item.setParentId(req.getParentId());
        item.setStoryPoint(req.getStoryPoint());
        item.setEstimateHours(req.getEstimateHours());
        item.setReleaseId(req.getReleaseId());
        item.setPlanStartDate(req.getPlanStartDate());
        item.setPlanEndDate(req.getPlanEndDate());
        return Result.ok(service.create(item));
    }

    @GetMapping("/api/items/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        WorkItem item = getOrThrow(id);
        Map<String, Object> vo = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        service.fill(rows, List.of(item));
        vo.putAll(rows.get(0));
        if (item.getParentId() != null) {
            WorkItem parent = itemMapper.selectById(item.getParentId());
            if (parent != null) {
                vo.put("parentTitle", parent.getTitle());
                vo.put("parentNo", parent.getItemNo());
            }
        }
        Long children = itemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getParentId, id));
        vo.put("childCount", children);
        List<WorkItem> childItems = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getParentId, id).orderByAsc(WorkItem::getId));
        List<Map<String, Object>> childRows = new ArrayList<>();
        service.fill(childRows, childItems);
        vo.put("children", childRows);
        vo.put("estimateHours", item.getEstimateHours());
        vo.put("remainingHours", item.getRemainingHours());
        vo.put("spentHours", item.getSpentHours());
        vo.put("releaseId", item.getReleaseId());
        return Result.ok(vo);
    }

    @PutMapping("/api/items/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ItemReq req) {
        WorkItem item = getOrThrow(id);
        service.update(item, toEntity(req));
        return Result.ok();
    }

    @DeleteMapping("/api/items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        itemMapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping("/api/items/{id}/transition")
    public Result<Void> transition(@PathVariable Long id, @RequestBody TransitionReq req) {
        WorkItem item = getOrThrow(id);
        service.transition(item, req.getStatusCode());
        return Result.ok();
    }

    // ---------- 评论 ----------

    @PostMapping("/api/items/{id}/comments")
    public Result<Map<String, Object>> addComment(@PathVariable Long id, @RequestBody CommentReq req) {
        WorkItem item = getOrThrow(id);
        WorkItemComment c = new WorkItemComment();
        c.setItemId(id);
        c.setUserId(CurrentUser.id());
        c.setContent(req.getContent());
        commentMapper.insert(c);
        service.logActivity(item, "COMMENT", Map.of("content", req.getContent()));
        User u = userMapper.selectById(c.getUserId());
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", c.getId());
        vo.put("itemId", c.getItemId());
        vo.put("userId", c.getUserId());
        vo.put("content", c.getContent());
        vo.put("createdAt", c.getCreatedAt());
        vo.put("nickname", u != null ? u.getNickname() : null);
        return Result.ok(vo);
    }

    @GetMapping("/api/items/{id}/comments")
    public Result<List<Map<String, Object>>> comments(@PathVariable Long id) {
        getOrThrow(id);
        List<WorkItemComment> comments = commentMapper.selectList(Wrappers.<WorkItemComment>lambdaQuery()
                .eq(WorkItemComment::getItemId, id).orderByAsc(WorkItemComment::getId));
        List<Long> userIds = comments.stream().map(WorkItemComment::getUserId).distinct().toList();
        Map<Long, String> nicknames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        return Result.ok(comments.stream().map(c -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", c.getId());
            vo.put("userId", c.getUserId());
            vo.put("content", c.getContent());
            vo.put("createdAt", c.getCreatedAt());
            vo.put("nickname", nicknames.get(c.getUserId()));
            return vo;
        }).toList());
    }

    // ---------- 动态 ----------

    @GetMapping("/api/items/{id}/activities")
    public Result<List<Map<String, Object>>> activities(@PathVariable Long id) {
        getOrThrow(id);
        List<WorkItemActivity> acts = activityMapper.selectList(Wrappers.<WorkItemActivity>lambdaQuery()
                .eq(WorkItemActivity::getItemId, id).orderByDesc(WorkItemActivity::getId));
        List<Long> userIds = acts.stream().map(WorkItemActivity::getUserId).distinct().toList();
        Map<Long, String> nicknames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getNickname));
        return Result.ok(acts.stream().map(a -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", a.getId());
            vo.put("action", a.getAction());
            vo.put("detail", a.getDetail());
            vo.put("createdAt", a.getCreatedAt());
            vo.put("nickname", nicknames.get(a.getUserId()));
            return vo;
        }).toList());
    }

    // ---------- 看板 ----------

    @GetMapping("/api/projects/{projectId}/board")
    public Result<Map<String, Object>> board(@PathVariable Long projectId,
                                             @RequestParam(defaultValue = "STORY") String type) {
        List<WorkItemStatus> statuses = statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                .eq(WorkItemStatus::getProjectId, projectId)
                .eq(WorkItemStatus::getType, type)
                .orderByAsc(WorkItemStatus::getSort));
        if (statuses.isEmpty()) {
            throw new ApiException("该项目未配置 " + type + " 的工作流状态");
        }
        List<WorkItem> items = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getType, type)
                .orderByDesc(WorkItem::getPriority));
        Map<String, String> nicknames = userMapper.selectList(Wrappers.<User>lambdaQuery()).stream()
                .collect(Collectors.toMap(u -> String.valueOf(u.getId()), User::getNickname));
        Map<String, List<Map<String, Object>>> columns = new java.util.LinkedHashMap<>();
        for (WorkItemStatus s : statuses) {
            columns.put(s.getCode(), new ArrayList<>());
        }
        for (WorkItem it : items) {
            Map<String, Object> card = new HashMap<>();
            card.put("id", it.getId());
            card.put("itemNo", it.getItemNo());
            card.put("title", it.getTitle());
            card.put("priority", it.getPriority());
            card.put("assigneeId", it.getAssigneeId());
            card.put("assigneeName", it.getAssigneeId() != null ? nicknames.get(String.valueOf(it.getAssigneeId())) : null);
            List<Map<String, Object>> col = columns.get(it.getStatusCode());
            if (col != null) {
                col.add(card);
            }
        }
        List<Map<String, Object>> columnList = statuses.stream().map(s -> {
            Map<String, Object> c = new HashMap<>();
            c.put("code", s.getCode());
            c.put("name", s.getName());
            c.put("category", s.getCategory());
            c.put("items", columns.get(s.getCode()));
            return c;
        }).toList();
        return Result.ok(Map.of("columns", columnList));
    }

    private WorkItem getOrThrow(Long id) {
        WorkItem item = itemMapper.selectById(id);
        if (item == null) {
            throw ApiException.notFound("工作项");
        }
        return item;
    }

    private WorkItem toEntity(ItemReq req) {
        WorkItem item = new WorkItem();
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setPriority(req.getPriority());
        item.setAssigneeId(req.getAssigneeId());
        item.setIterationId(req.getIterationId());
        item.setStoryPoint(req.getStoryPoint());
        item.setEstimateHours(req.getEstimateHours());
        item.setReleaseId(req.getReleaseId());
        item.setPlanStartDate(req.getPlanStartDate());
        item.setPlanEndDate(req.getPlanEndDate());
        return item;
    }
}
