package com.devfusion.deploy;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 部署单：把某次构建的制品部署到指定环境 */
@Data
@TableName("deployment")
public class Deployment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private Long artifactId;

    private Long environmentId;

    /** 冗余制品版本，展示用 */
    private String version;

    /** DEPLOY 正常部署 / ROLLBACK 回滚部署 */
    private String deployType;

    /** 回滚单指向的原部署单 */
    private Long rollbackOfId;

    /** 可选部署模板 */
    private Long deployTemplateId;

    /** PENDING / PENDING_APPROVAL / DEPLOYING / SUCCESS / FAILED / CANCELED / REJECTED */
    private String status;

    private Long operatorId;

    private String log;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
