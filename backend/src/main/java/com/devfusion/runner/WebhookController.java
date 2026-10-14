package com.devfusion.runner;

import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.pipeline.Pipeline;
import com.devfusion.pipeline.mapper.PipelineMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 代码仓库/外部系统 webhook 触发构建（免登录，按 token 鉴别） */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final PipelineMapper pipelineMapper;
    private final RunService runService;

    public WebhookController(PipelineMapper pipelineMapper, RunService runService) {
        this.pipelineMapper = pipelineMapper;
        this.runService = runService;
    }

    @PostMapping("/pipelines/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id,
                                               @RequestParam(required = false) String token) {
        Pipeline pipeline = pipelineMapper.selectById(id);
        if (pipeline == null || token == null || !token.equals(pipeline.getWebhookToken())) {
            throw new ApiException(403, "webhook 鉴权失败");
        }
        if (!"WEBHOOK".equals(pipeline.getTriggerType())) {
            throw new ApiException("该流水线未启用 webhook 触发");
        }
        PipelineRun run = runService.trigger(id, "WEBHOOK", null);
        return Result.ok(Map.of("runId", run.getId(), "version", run.getVersion()));
    }
}
