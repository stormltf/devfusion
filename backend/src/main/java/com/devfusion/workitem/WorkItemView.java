package com.devfusion.workitem;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 已保存视图：私有视图仅创建者可见，公共视图项目成员可见 */
@Data
@TableName("work_item_view")
public class WorkItemView {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String type;

    private String name;

    private Integer isPublic;

    private Long ownerId;

    /** JSON 串：筛选条件 */
    private String filterJson;

    /** JSON 串：{field, direction} */
    private String sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
