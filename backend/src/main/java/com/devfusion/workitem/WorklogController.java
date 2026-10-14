package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.common.UserNameResolver;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import com.devfusion.workitem.mapper.WorklogMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 工时：登记/删除，回写 work_item.spent_hours / remaining_hours */
@RestController
public class WorklogController {

    private final WorklogMapper worklogMapper;
    private final WorkItemMapper itemMapper;
    private final UserMapper userMapper;
    private final UserNameResolver userNameResolver;

    public WorklogController(WorklogMapper worklogMapper, WorkItemMapper itemMapper, UserMapper userMapper,
                             UserNameResolver userNameResolver) {
        this.worklogMapper = worklogMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.userNameResolver = userNameResolver;
    }

    @Data
    public static class LogReq {
        private Double hours;
        private Double remaining;
        private LocalDate logDate;
        private String note;
    }

    @GetMapping("/api/items/{id}/worklogs")
    public Result<List<Map<String, Object>>> list(@PathVariable Long id) {
        getOrThrow(id);
        List<Worklog> logs = worklogMapper.selectList(Wrappers.<Worklog>lambdaQuery()
                .eq(Worklog::getItemId, id).orderByDesc(Worklog::getId));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        Map<Long, String> nicknames = userNameResolver.names(logs.stream().map(Worklog::getUserId).toList());
        for (Worklog l : logs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("itemId", l.getItemId());
            m.put("userId", l.getUserId());
            m.put("userName", nicknames.get(l.getUserId()));
            m.put("hours", l.getHours());
            m.put("remaining", l.getRemaining());
            m.put("logDate", l.getLogDate());
            m.put("note", l.getNote());
            m.put("createdAt", l.getCreatedAt());
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/api/items/{id}/worklogs")
    @Transactional
    public Result<Void> add(@PathVariable Long id, @RequestBody LogReq req) {
        WorkItem item = getOrThrow(id);
        if (req.getHours() == null || req.getHours() <= 0) {
            throw new ApiException("hours 必须大于 0");
        }
        Worklog l = new Worklog();
        l.setItemId(id);
        l.setUserId(CurrentUser.id());
        l.setHours(req.getHours());
        l.setRemaining(req.getRemaining());
        l.setLogDate(req.getLogDate() == null ? LocalDate.now() : req.getLogDate());
        l.setNote(req.getNote());
        worklogMapper.insert(l);
        if (req.getRemaining() != null) {
            item.setRemainingHours(req.getRemaining());
        }
        item.setSpentHours(sumHours(id));
        itemMapper.updateById(item);
        return Result.ok();
    }

    @DeleteMapping("/api/items/{id}/worklogs/{logId}")
    @Transactional
    public Result<Void> remove(@PathVariable Long id, @PathVariable Long logId) {
        getOrThrow(id);
        worklogMapper.deleteById(logId);
        WorkItem item = getOrThrow(id);
        item.setSpentHours(sumHours(id));
        itemMapper.updateById(item);
        return Result.ok();
    }

    private Double sumHours(Long itemId) {
        List<Worklog> logs = worklogMapper.selectList(Wrappers.<Worklog>lambdaQuery()
                .eq(Worklog::getItemId, itemId));
        double sum = logs.stream().mapToDouble(l -> l.getHours() == null ? 0 : l.getHours()).sum();
        return Math.round(sum * 100.0) / 100.0;
    }

    private WorkItem getOrThrow(Long id) {
        WorkItem item = itemMapper.selectById(id);
        if (item == null) {
            throw ApiException.notFound("工作项");
        }
        return item;
    }
}
