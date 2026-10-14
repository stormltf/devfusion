package com.devfusion.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** BUILD_FAILED / APPROVAL_PENDING / DEPLOY_RESULT / WORK_ITEM_ASSIGNED ... */
    private String type;

    private String title;

    private String content;

    private String link;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
