package com.devfusion.runner;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 运行时任务快照，含配置与日志 */
@Data
@TableName("run_job")
public class RunJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long runStageId;

    private Long jobId;

    private String name;

    private String type;

    private String config;

    private Integer continueOnFail;

    @com.baomidou.mybatisplus.annotation.TableField("`parallel`")
    private Integer parallel;

    private Integer timeoutMinutes;

    /** PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / CANCELED / WAITING_APPROVAL / TIMEOUT */
    private String status;

    private String log;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
