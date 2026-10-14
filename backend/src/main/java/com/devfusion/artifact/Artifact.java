package com.devfusion.artifact;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 构建产物（简单云“半成品库”语义：构建版本，可被部署单引用） */
@Data
@TableName("artifact")
public class Artifact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    /** 版本号，如 GWP-202610131530-12 */
    private String version;

    private Long pipelineRunId;

    /** JAR / IMAGE / NPM */
    private String type;

    /** SNAPSHOT 半成品 / RELEASE 成品 */
    private String stage;

    /** 成品三位发布版本（如 1.0.3） */
    private String releaseVersion;

    private String path;

    private Long sizeBytes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
