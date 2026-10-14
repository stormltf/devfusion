package com.devfusion.runner;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 构建记录 <-> 工作项 关联（解析 commitMessage 中的编号自动建立） */
@Data
@TableName("run_work_item")
public class RunWorkItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long workItemId;

    private String workItemNo;

    private LocalDateTime createdAt;
}
