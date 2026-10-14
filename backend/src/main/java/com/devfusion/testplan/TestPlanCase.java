package com.devfusion.testplan;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 计划内用例：plan_id+case_id 唯一，存该计划下最新执行人/结果 */
@Data
@TableName("test_plan_case")
public class TestPlanCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long caseId;

    private Long executorId;

    /** UNEXECUTED / PASS / FAIL / BLOCKED / SKIPPED */
    private String result;

    private String note;

    private LocalDateTime executedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
