package com.devfusion.workflow;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("work_item_status")
public class WorkItemStatus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** STORY / TASK / BUG */
    private String type;

    /** 状态编码，如 TODO、IN_PROGRESS、DONE、CLOSED */
    private String code;

    private String name;

    /** TODO / IN_PROGRESS / DONE */
    private String category;

    private Integer sort;

    /** 1 初始状态 0 非初始 */
    private Integer isInitial;

    /** 1 终态 0 非终态 */
    private Integer isFinal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
