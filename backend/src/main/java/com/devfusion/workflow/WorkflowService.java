package com.devfusion.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.workflow.mapper.WorkItemStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 各类型工作项的默认状态模板；新项目创建时调用 initForProject */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkItemStatusMapper statusMapper;

    public record Template(String code, String name, String category, int sort, int initial, int fin) {
    }

    private static final List<Template> STORY = List.of(
            new Template("TODO", "待处理", "TODO", 1, 1, 0),
            new Template("IN_PROGRESS", "进行中", "IN_PROGRESS", 2, 0, 0),
            new Template("DONE", "已完成", "DONE", 3, 0, 1));

    private static final List<Template> TASK = List.of(
            new Template("TODO", "待办", "TODO", 1, 1, 0),
            new Template("IN_PROGRESS", "进行中", "IN_PROGRESS", 2, 0, 0),
            new Template("DONE", "已完成", "DONE", 3, 0, 1));

    private static final List<Template> BUG = List.of(
            new Template("NEW", "新建", "TODO", 1, 1, 0),
            new Template("FIXING", "处理中", "IN_PROGRESS", 2, 0, 0),
            new Template("FIXED", "已修复", "DONE", 3, 0, 0),
            new Template("CLOSED", "已关闭", "DONE", 4, 0, 1));

    @Transactional
    public void initForProject(Long projectId) {
        insertTemplates(projectId, "STORY", STORY);
        insertTemplates(projectId, "TASK", TASK);
        insertTemplates(projectId, "BUG", BUG);
    }

    private void insertTemplates(Long projectId, String type, List<Template> templates) {
        templates.forEach(t -> {
            WorkItemStatus s = new WorkItemStatus();
            s.setProjectId(projectId);
            s.setType(type);
            s.setCode(t.code());
            s.setName(t.name());
            s.setCategory(t.category());
            s.setSort(t.sort());
            s.setIsInitial(t.initial());
            s.setIsFinal(t.fin());
            statusMapper.insert(s);
        });
    }

    public List<WorkItemStatus> listByProject(Long projectId) {
        return statusMapper.selectList(Wrappers.<WorkItemStatus>lambdaQuery()
                .eq(WorkItemStatus::getProjectId, projectId)
                .orderByAsc(WorkItemStatus::getType).orderByAsc(WorkItemStatus::getSort));
    }
}
