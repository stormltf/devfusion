package com.devfusion.pipeline;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 流水线参数变量（触发时可被覆盖，密文打码） */
@Data
@TableName("pipeline_variable")
public class PipelineVariable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pipelineId;

    private String name;

    private String defaultValue;

    /** 1=密文，日志中打码 */
    private Integer secret;

    /** 1=手动触发时允许修改 */
    private Integer runtimeEditable;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
