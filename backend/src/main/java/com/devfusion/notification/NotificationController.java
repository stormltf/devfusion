package com.devfusion.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.notification.mapper.NotificationMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 站内通知：列表 / 未读数 / 标记已读 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationMapper mapper;

    public NotificationController(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<List<Notification>> list() {
        return Result.ok(mapper.selectList(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, CurrentUser.id())
                .orderByDesc(Notification::getId).last("LIMIT 50")));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        long count = mapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, CurrentUser.id())
                .isNull(Notification::getReadAt));
        Map<String, Object> m = new HashMap<>();
        m.put("count", count);
        return Result.ok(m);
    }

    @PostMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        Notification n = mapper.selectById(id);
        if (n != null && n.getUserId().equals(CurrentUser.id()) && n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            mapper.updateById(n);
        }
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        List<Notification> unread = mapper.selectList(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, CurrentUser.id())
                .isNull(Notification::getReadAt));
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> {
            n.setReadAt(now);
            mapper.updateById(n);
        });
        return Result.ok();
    }
}
