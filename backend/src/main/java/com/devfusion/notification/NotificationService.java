package com.devfusion.notification;

import com.devfusion.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知发送入口。其他模块（含项目管理侧“工作项指派”）统一调用 send(...)。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public Notification send(Long userId, String type, String title, String content, String link) {
        if (userId == null) {
            return null;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setLink(link);
        notificationMapper.insert(n);
        return n;
    }

    public void sendToEach(List<Long> userIds, String type, String title, String content, String link) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(uid -> send(uid, type, title, content, link));
    }
}
