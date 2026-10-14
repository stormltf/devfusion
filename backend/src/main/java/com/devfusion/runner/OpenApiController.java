package com.devfusion.runner;

import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.token.AccessToken;
import com.devfusion.token.TokenService;
import lombok.Data;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** OpenAPI：用个人访问令牌（PAT）替代登录态触发流水线。路径已在拦截器放行。 */
@RestController
@RequestMapping("/api/open")
public class OpenApiController {

    private final RunService runService;
    private final TokenService tokenService;

    public OpenApiController(RunService runService, TokenService tokenService) {
        this.runService = runService;
        this.tokenService = tokenService;
    }

    @Data
    public static class TriggerReq {
        private Map<String, String> variables;
        private String branch;
        private String commitMessage;
    }

    @PostMapping("/pipelines/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable Long id,
                                               @RequestHeader(value = "Authorization", required = false) String auth,
                                               @RequestBody(required = false) TriggerReq req) {
        String plain = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : auth;
        AccessToken token = tokenService.verify(plain);
        if (token == null) {
            throw new ApiException(401, "无效或过期的个人访问令牌");
        }
        PipelineRun run = runService.trigger(id, "OPENAPI", token.getUserId(),
                req == null ? null : req.getVariables(),
                req == null ? null : req.getBranch(),
                req == null ? null : req.getCommitMessage());
        return Result.ok(Map.of("runId", run.getId(), "version", run.getVersion()));
    }
}
