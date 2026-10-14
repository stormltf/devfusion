package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.iteration.Iteration;
import com.devfusion.iteration.mapper.IterationMapper;
import com.devfusion.project.Project;
import com.devfusion.project.ProjectMember;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.project.mapper.ProjectMemberMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workflow.WorkItemStatus;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import com.devfusion.workitem.mapper.WorkItemActivityMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkItemService {

    public static final String SEQ_KEY_PREFIX = "df:seq:";

    private final WorkItemMapper itemMapper;
    private final WorkItemStatusMapper statusMapper;
    private final WorkItemActivityMapper activityMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final IterationMapper iterationMapper;
    private final StringRedisTemplate redis;

    public WorkItemService(WorkItemMapper itemMapper, WorkItemStatusMapper statusMapper,
                           WorkItemActivityMapper activityMapper, ProjectMapper projectMapper,
                           ProjectMemberMapper memberMapper, UserMapper userMapper,
                           IterationMapper iterationMapper, StringRedisTemplate redis) {
        this.itemMapper = itemMapper;
        this.statusMapper = statusMapper;
        this.activityMapper = activityMapper;
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.iterationMapper = iterationMapper;
        this.redis = redis;
    }

    @Transactional
    public WorkItem create(WorkItem item) {
        Project project = projectMapper.selectById(item.getProjectId());
        if (project == null) {
            throw ApiException.notFound("项目");
        }
        if (List.of("STORY", "TASK", "BUG").indexOf(item.getType()) < 0) {
            throw new ApiException("type 必须是 STORY/TASK/BUG");
        }
        if (item.getAssigneeId() != null) {
            requireMember(item.getProjectId(), item.getAssigneeId());
        }
        // 默认初始状态
        if (item.getStatusCode() == null || item.getStatusCode().isBlank()) {
            WorkItemStatus initial = statusMapper.selectOne(Wrappers.<WorkItemStatus>lambdaQuery()
                    .eq(WorkItemStatus::getProjectId, item.getProjectId())
                    .eq(WorkItemStatus::getType, item.getType())
                    .eq(WorkItemStatus::getIsInitial, 1));
            if (initial == null) {
                throw new ApiException("该项目未配置 " + item.getType() + " 的工作流状态");
            }
            item.setStatusCode(initial.getCode());
        } else {
            requireStatus(item.getProjectId(), item.getType(), item.getStatusCode());
        }
        if (item.getIterationId() != null) {
            requireIteration(item.getProjectId(), item.getIterationId());
        }
        if (item.getParentId() != null) {
            WorkItem parent = itemMapper.selectById(item.getParentId());
            if (parent == null || !parent.getProjectId().equals(item.getProjectId())) {
                throw new ApiException("父工作项不存在");
            }
        }
        if (item.getPriority() == null) {
            item.setPriority("MEDIUM");
        }

        // 编号：项目KEY-TYPE-序号，序号取 Redis 自增保证并发原子
        Long seq = redis.opsForValue().increment(SEQ_KEY_PREFIX + item.getProjectId() + ":" + item.getType());
        item.setItemNo(project.getKey() + "-" + item.getType() + "-" + seq);
        item.setReporterId(com.devfusion.common.CurrentUser.id());
        itemMapper.insert(item);

        logActivity(item, "CREATE", Map.of("title", item.getTitle()));
        return item;
    }

    @Transactional
    public void transition(WorkItem item, String toCode) {
        requireStatus(item.getProjectId(), item.getType(), toCode);
        String from = item.getStatusCode();
        if (from.equals(toCode)) {
            return;
        }
        item.setStatusCode(toCode);
        itemMapper.updateById(item);
        logActivity(item, "STATUS", Map.of("from", from, "to", toCode));
    }

    @Transactional
    public void update(WorkItem item, WorkItem req) {
        StringBuilder diff = new StringBuilder();
        if (req.getTitle() != null && !req.getTitle().equals(item.getTitle())) {
            diff.append("标题;");
            item.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            item.setDescription(req.getDescription());
        }
        if (req.getPriority() != null && !req.getPriority().equals(item.getPriority())) {
            diff.append("优先级;");
            item.setPriority(req.getPriority());
        }
        if (req.getIterationId() != null && !req.getIterationId().equals(item.getIterationId())) {
            requireIteration(item.getProjectId(), req.getIterationId());
            diff.append("迭代;");
            item.setIterationId(req.getIterationId());
        }
        if (req.getStoryPoint() != null) {
            item.setStoryPoint(req.getStoryPoint());
        }
        if (req.getEstimateHours() != null) {
            item.setEstimateHours(req.getEstimateHours());
        }
        if (req.getReleaseId() != null && !req.getReleaseId().equals(item.getReleaseId())) {
            item.setReleaseId(req.getReleaseId());
            diff.append("发布;");
        }
        if (req.getPlanStartDate() != null) {
            item.setPlanStartDate(req.getPlanStartDate());
        }
        if (req.getPlanEndDate() != null) {
            item.setPlanEndDate(req.getPlanEndDate());
        }
        if (req.getAssigneeId() != null && !req.getAssigneeId().equals(item.getAssigneeId())) {
            requireMember(item.getProjectId(), req.getAssigneeId());
            diff.append("负责人;");
            item.setAssigneeId(req.getAssigneeId());
            logActivity(item, "ASSIGN", Map.of("assigneeId", req.getAssigneeId()));
        }
        itemMapper.updateById(item);
        if (!diff.isEmpty()) {
            logActivity(item, "UPDATE", Map.of("fields", diff.toString()));
        }
    }

    public void logActivity(WorkItem item, String action, Map<String, Object> detail) {
        WorkItemActivity a = new WorkItemActivity();
        a.setItemId(item.getId());
        a.setProjectId(item.getProjectId());
        a.setUserId(com.devfusion.common.CurrentUser.id());
        a.setAction(action);
        try {
            a.setDetail(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detail));
        } catch (Exception e) {
            a.setDetail("{}");
        }
        activityMapper.insert(a);
    }

    /** 列表 VO 填充：负责人昵称、迭代名、状态名 */
    public void fill(List<Map<String, Object>> rows, List<WorkItem> items) {
        List<Long> userIds = items.stream().map(WorkItem::getAssigneeId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getId, User::getNickname));
        List<Long> iterIds = items.stream().map(WorkItem::getIterationId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> iterNames = iterIds.isEmpty() ? Map.of()
                : iterationMapper.selectBatchIds(iterIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Iteration::getId, Iteration::getName));
        Map<String, String> statusNames = statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                        .eq(WorkItemStatus::getProjectId, items.isEmpty() ? -1 : items.get(0).getProjectId()))
                .stream().collect(java.util.stream.Collectors.toMap(WorkItemStatus::getCode,
                        WorkItemStatus::getName, (a, b) -> a));

        for (WorkItem it : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", it.getId());
            row.put("projectId", it.getProjectId());
            row.put("type", it.getType());
            row.put("itemNo", it.getItemNo());
            row.put("title", it.getTitle());
            row.put("description", it.getDescription());
            row.put("statusCode", it.getStatusCode());
            row.put("statusName", statusNames.get(it.getStatusCode()));
            row.put("priority", it.getPriority());
            row.put("assigneeId", it.getAssigneeId());
            row.put("assigneeName", it.getAssigneeId() == null ? null : nicknames.get(it.getAssigneeId()));
            row.put("reporterId", it.getReporterId());
            row.put("iterationId", it.getIterationId());
            row.put("iterationName", it.getIterationId() == null ? null : iterNames.get(it.getIterationId()));
            row.put("parentId", it.getParentId());
            row.put("storyPoint", it.getStoryPoint());
            row.put("estimateHours", it.getEstimateHours());
            row.put("remainingHours", it.getRemainingHours());
            row.put("spentHours", it.getSpentHours());
            row.put("releaseId", it.getReleaseId());
            row.put("planStartDate", it.getPlanStartDate());
            row.put("planEndDate", it.getPlanEndDate());
            row.put("createdAt", it.getCreatedAt());
            row.put("updatedAt", it.getUpdatedAt());
            rows.add(row);
        }
    }

    private void requireMember(Long projectId, Long userId) {
        Long count = memberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (count == 0) {
            throw new ApiException("指派对象必须是项目成员");
        }
    }

    private void requireStatus(Long projectId, String type, String code) {
        Long count = statusMapper.selectCount(Wrappers.<WorkItemStatus>lambdaQuery()
                .eq(WorkItemStatus::getProjectId, projectId)
                .eq(WorkItemStatus::getType, type)
                .eq(WorkItemStatus::getCode, code));
        if (count == 0) {
            throw new ApiException("状态 " + code + " 在该项目的工作流中不存在");
        }
    }

    private void requireIteration(Long projectId, Long iterationId) {
        Iteration it = iterationMapper.selectById(iterationId);
        if (it == null || !it.getProjectId().equals(projectId)) {
            throw new ApiException("迭代不存在或不属于该项目");
        }
    }
}
