package com.devfusion.testcase;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("test_case")
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 项目内唯一编号，如 GWP-TC-3 */
    private String caseNo;

    private String title;

    private String precondition;

    /** JSON 数组：[{"action":"...","expected":"..."}] */
    private String steps;

    private String expected;

    /** URGENT / HIGH / MEDIUM / LOW */
    private String priority;

    private String module;

    /** UNEXECUTED / PASS / FAIL / BLOCKED */
    private String latestResult;

    private Long latestExecutorId;

    private LocalDateTime latestExecutedAt;

    /** 最近一次 FAIL 关联的缺陷工作项 */
    private Long bugItemId;

    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
