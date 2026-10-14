package com.devfusion.pipeline;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pipeline_stage")
public class PipelineStage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pipelineId;

    private String name;

    private Integer sort;
}
