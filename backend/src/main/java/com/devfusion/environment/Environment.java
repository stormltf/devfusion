package com.devfusion.environment;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 部署环境（参考简单云：环境由用户自建，type 仅作标签） */
@Data
@TableName("environment")
public class Environment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("`key`")
    private String key;

    private String name;

    /** DEV / TEST / STAGING / PROD */
    private String type;

    private String description;

    /** 主机/集群信息描述 */
    private String hosts;

    /** 1=部署到该环境需要审批 */
    private Integer requireApproval;

    private Long hostGroupId;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
