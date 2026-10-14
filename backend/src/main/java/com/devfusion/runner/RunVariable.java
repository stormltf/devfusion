package com.devfusion.runner;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单次构建的变量快照（系统变量 + 用户变量合并结果） */
@Data
@TableName("run_variable")
public class RunVariable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String name;

    private String value;

    private Integer secret;

    private LocalDateTime createdAt;
}
