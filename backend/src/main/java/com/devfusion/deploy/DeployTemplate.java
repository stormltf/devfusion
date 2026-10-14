package com.devfusion.deploy;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 部署模板：前置/后置脚本与健康检查地址，部署单可选复用 */
@Data
@TableName("deploy_template")
public class DeployTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private String name;

    private String preScript;

    private String postScript;

    private String healthCheckUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
