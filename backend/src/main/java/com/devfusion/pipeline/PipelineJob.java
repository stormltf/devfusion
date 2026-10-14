package com.devfusion.pipeline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pipeline_job")
public class PipelineJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stageId;

    private String name;

    /**
     * SHELL / MAVEN_BUILD / NPM_BUILD / DOCKER_BUILD / UNIT_TEST / ARTIFACT_UPLOAD
     * / APPROVAL / TRIGGER_PIPELINE
     */
    private String type;

    /** JSON 配置：SHELL=script，UNIT_TEST=testCases，MAVEN_BUILD=goals 等 */
    private String config;

    /** 1 失败继续 0 失败中断 */
    private Integer continueOnFail;

    /** 1=与同 stage 内其他 parallel job 并发执行 */
    @com.baomidou.mybatisplus.annotation.TableField("`parallel`")
    private Integer parallel;

    /** 任务超时分钟（null=不限制） */
    private Integer timeoutMinutes;

    private Integer sort;
}
