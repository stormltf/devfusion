package com.devfusion.runner;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 运行时阶段快照（编辑流水线不影响进行中的 run） */
@Data
@TableName("run_stage")
public class RunStage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long stageId;

    private String name;

    private Integer sort;

    /** PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / CANCELED */
    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
