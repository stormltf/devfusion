package com.devfusion.runner;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_run")
public class PipelineRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pipelineId;

    private Long applicationId;

    /** 构建版本号，如 GWP-202610131530-12 */
    private String version;

    /** MANUAL / WEBHOOK */
    private String triggerType;

    private Long triggerUserId;

    /** PENDING / RUNNING / SUCCESS / FAILED / CANCELED */
    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
