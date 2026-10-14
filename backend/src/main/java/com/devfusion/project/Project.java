package com.devfusion.project;

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
@TableName("`project`")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短标识，用于工作项编号前缀，如 GWP */
    @TableField("`key`")
    private String key;

    private String name;

    private String description;

    private Long ownerId;

    /** ACTIVE 进行中 / CLOSED 已结束 / ARCHIVED 已归档 */
    private String status;

    private String color;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
