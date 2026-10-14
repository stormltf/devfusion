package com.devfusion.workitem;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作项操作动态，detail 为 JSON 字符串（如 {"from":"TODO","to":"DONE"}） */
@Data
@TableName("work_item_activity")
public class WorkItemActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long projectId;

    private Long userId;

    /** CREATE / UPDATE / STATUS / ASSIGN / COMMENT */
    private String action;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
