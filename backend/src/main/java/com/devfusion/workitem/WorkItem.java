package com.devfusion.workitem;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("work_item")
public class WorkItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** STORY / TASK / BUG */
    private String type;

    /** 项目内唯一编号，如 GWP-12 */
    private String itemNo;

    private String title;

    private String description;

    /** 对应 work_item_status.code */
    private String statusCode;

    /** URGENT / HIGH / MEDIUM / LOW */
    private String priority;

    private Long assigneeId;

    private Long reporterId;

    private Long iterationId;

    /** 父工作项（需求拆任务/子缺陷） */
    private Long parentId;

    private Double storyPoint;

    private Double estimateHours;

    private Double remainingHours;

    private Double spentHours;

    private Long releaseId;

    private LocalDate planStartDate;

    private LocalDate planEndDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
