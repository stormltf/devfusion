package com.devfusion.pipeline;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline")
public class Pipeline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private String name;

    private String description;

    /** MANUAL / WEBHOOK / SCHEDULE */
    private String triggerType;

    /** webhook 触发令牌 */
    private String webhookToken;

    /** 定时触发 Cron 表达式（triggerType=SCHEDULE 时生效） */
    private String cron;

    /** ENABLED / DISABLED */
    private String status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
