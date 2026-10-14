package com.devfusion.runner;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.runner.mapper.PipelineRunMapper;
import com.devfusion.runner.mapper.RunJobMapper;
import com.devfusion.runner.mapper.RunStageMapper;
import com.devfusion.runner.mapper.RunWorkItemMapper;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.devfusion.pipeline.mapper.PipelineMapper;

@RestController
public class RunController {

    private final RunService runService;
    private final PipelineRunMapper runMapper;
    private final RunStageMapper runStageMapper;
    private final RunJobMapper runJobMapper;
    private final UserMapper userMapper;
    private final com.devfusion.pipeline.mapper.PipelineMapper pipelineMapper;
    private final PipelineExecutor executor;
    private final RunWorkItemMapper runWorkItemMapper;
    private final StringRedisTemplate redis;

    public RunController(RunService runService, PipelineRunMapper runMapper, RunStageMapper runStageMapper,
                         RunJobMapper runJobMapper, UserMapper userMapper,
                         com.devfusion.pipeline.mapper.PipelineMapper pipelineMapper,
                         PipelineExecutor executor, RunWorkItemMapper runWorkItemMapper,
                         StringRedisTemplate redis) {
        this.runService = runService;
        this.runMapper = runMapper;
        this.runStageMapper = runStageMapper;
        this.runJobMapper = runJobMapper;
        this.userMapper = userMapper;
        this.pipelineMapper = pipelineMapper;
        this.executor = executor;
        this.runWorkItemMapper = runWorkItemMapper;
        this.redis = redis;
    }

    @Data
    public static class TriggerReq {
        private Map<String, String> variables;
        private String branch;
        private String commitMessage;
    }

    @PostMapping("/api/pipelines/{id}/run")
    public Result<PipelineRun> trigger(@PathVariable Long id, @RequestBody(required = false) TriggerReq req) {
        Map<String, String> vars = req == null ? null : req.getVariables();
        String branch = req == null ? null : req.getBranch();
        String commitMessage = req == null ? null : req.getCommitMessage();
        PipelineRun run = runService.trigger(id, "MANUAL", CurrentUser.id(), vars, branch, commitMessage);
        run.setTriggerUserId(null);
        return Result.ok(run);
    }

    /** 人工审批通过：续跑 */
    @PostMapping("/api/runs/{id}/jobs/{jobId}/approve")
    public Result<Void> approve(@PathVariable Long id, @PathVariable Long jobId) {
        RunJob job = requireApprovalJob(id, jobId);
        executor.onApprovalDecision(jobId, true);
        return Result.ok();
    }

    /** 人工审批拒绝：任务失败 */
    @PostMapping("/api/runs/{id}/jobs/{jobId}/reject")
    public Result<Void> reject(@PathVariable Long id, @PathVariable Long jobId) {
        RunJob job = requireApprovalJob(id, jobId);
        executor.onApprovalDecision(jobId, false);
        return Result.ok();
    }

    private RunJob requireApprovalJob(Long runId, Long jobId) {
        RunJob job = runJobMapper.selectById(jobId);
        if (job == null || !job.getRunId().equals(runId)) {
            throw ApiException.notFound("任务");
        }
        if (!"WAITING_APPROVAL".equals(job.getStatus())) {
            throw new ApiException("该任务当前不在待审批状态");
        }
        return job;
    }

    /** 本次构建关联的工作项 */
    @GetMapping("/api/runs/{id}/work-items")
    public Result<List<Map<String, Object>>> runWorkItems(@PathVariable Long id) {
        getOrThrow(id);
        List<RunWorkItem> links = runWorkItemMapper.selectList(Wrappers.<RunWorkItem>lambdaQuery()
                .eq(RunWorkItem::getRunId, id));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RunWorkItem l : links) {
            Map<String, Object> m = new HashMap<>();
            m.put("workItemId", l.getWorkItemId());
            m.put("workItemNo", l.getWorkItemNo());
            rows.add(m);
        }
        return Result.ok(rows);
    }

    /** 工作项关联的构建记录（反查，只读） */
    @GetMapping("/api/work-items/{itemId}/runs")
    public Result<List<Map<String, Object>>> workItemRuns(@PathVariable Long itemId) {
        List<RunWorkItem> links = runWorkItemMapper.selectList(Wrappers.<RunWorkItem>lambdaQuery()
                .eq(RunWorkItem::getWorkItemId, itemId).orderByDesc(RunWorkItem::getRunId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RunWorkItem l : links) {
            PipelineRun r = runMapper.selectById(l.getRunId());
            if (r == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("runId", r.getId());
            m.put("version", r.getVersion());
            m.put("status", r.getStatus());
            m.put("triggerType", r.getTriggerType());
            m.put("createdAt", r.getCreatedAt());
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @GetMapping("/api/pipelines/{id}/runs")
    public Result<PageResult<Map<String, Object>>> runsOf(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size) {
        Page<PipelineRun> p = runMapper.selectPage(new Page<>(page, size),
                Wrappers.<PipelineRun>lambdaQuery()
                        .eq(PipelineRun::getPipelineId, id)
                        .orderByDesc(PipelineRun::getId));
        return Result.ok(toPageVo(p));
    }

    @GetMapping("/api/runs")
    public Result<PageResult<Map<String, Object>>> allRuns(@RequestParam(required = false) Long applicationId,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        Page<PipelineRun> p = runMapper.selectPage(new Page<>(page, size),
                Wrappers.<PipelineRun>lambdaQuery()
                        .eq(applicationId != null, PipelineRun::getApplicationId, applicationId)
                        .orderByDesc(PipelineRun::getId));
        return Result.ok(toPageVo(p));
    }

    @GetMapping("/api/runs/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        PipelineRun run = getOrThrow(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", run.getId());
        m.put("pipelineId", run.getPipelineId());
        m.put("applicationId", run.getApplicationId());
        m.put("version", run.getVersion());
        m.put("triggerType", run.getTriggerType());
        m.put("status", run.getStatus());
        m.put("startedAt", run.getStartedAt());
        m.put("finishedAt", run.getFinishedAt());
        m.put("durationMs", run.getDurationMs());
        if (run.getTriggerUserId() != null) {
            User u = userMapper.selectById(run.getTriggerUserId());
            m.put("triggerUserName", u != null ? u.getNickname() : null);
        }
        List<Map<String, Object>> stages = new ArrayList<>();
        for (RunStage s : runStageMapper.selectList(Wrappers.<RunStage>lambdaQuery()
                .eq(RunStage::getRunId, id).orderByAsc(RunStage::getSort))) {
            Map<String, Object> sm = new HashMap<>();
            sm.put("id", s.getId());
            sm.put("name", s.getName());
            sm.put("sort", s.getSort());
            sm.put("status", s.getStatus());
            sm.put("startedAt", s.getStartedAt());
            sm.put("finishedAt", s.getFinishedAt());
            List<Map<String, Object>> jobs = new ArrayList<>();
            for (RunJob j : runJobMapper.selectList(Wrappers.<RunJob>lambdaQuery()
                    .eq(RunJob::getRunStageId, s.getId()).orderByAsc(RunJob::getId))) {
                Map<String, Object> jm = new HashMap<>();
                jm.put("id", j.getId());
                jm.put("name", j.getName());
                jm.put("type", j.getType());
                jm.put("status", j.getStatus());
                jm.put("log", j.getLog());
                jm.put("startedAt", j.getStartedAt());
                jm.put("finishedAt", j.getFinishedAt());
                jobs.add(jm);
            }
            sm.put("jobs", jobs);
            stages.add(sm);
        }
        m.put("stages", stages);
        return Result.ok(m);
    }

    @PostMapping("/api/runs/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        PipelineRun run = getOrThrow(id);
        if (!"PENDING".equals(run.getStatus()) && !"RUNNING".equals(run.getStatus())
                && !"WAITING_APPROVAL".equals(run.getStatus())) {
            throw new ApiException("仅排队中、运行中或待审批的构建可以取消");
        }
        redis.opsForValue().set(PipelineExecutor.CANCEL_KEY_PREFIX + id, "1", java.time.Duration.ofHours(1));
        // 若正挂起在审批点，唤醒其检查取消标志
        List<RunJob> waiting = runJobMapper.selectList(Wrappers.<RunJob>lambdaQuery()
                .eq(RunJob::getRunId, id).eq(RunJob::getStatus, "WAITING_APPROVAL"));
        for (RunJob j : waiting) {
            executor.onApprovalDecision(j.getId(), false);
        }
        return Result.ok();
    }

    private PipelineRun getOrThrow(Long id) {
        PipelineRun run = runMapper.selectById(id);
        if (run == null) {
            throw ApiException.notFound("构建记录");
        }
        return run;
    }

    private PageResult<Map<String, Object>> toPageVo(Page<PipelineRun> p) {
        Map<Long, String> pipelineNames = new HashMap<>();
        Map<Long, String> userNames = new HashMap<>();
        List<Map<String, Object>> rows = p.getRecords().stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("pipelineId", r.getPipelineId());
            m.put("applicationId", r.getApplicationId());
            m.put("version", r.getVersion());
            m.put("triggerType", r.getTriggerType());
            m.put("status", r.getStatus());
            m.put("startedAt", r.getStartedAt());
            m.put("finishedAt", r.getFinishedAt());
            m.put("durationMs", r.getDurationMs());
            m.put("pipelineName", pipelineNames.computeIfAbsent(r.getPipelineId(),
                    pid -> {
                        var pl = pipelineMapper.selectById(pid);
                        return pl != null ? pl.getName() : null;
                    }));
            if (r.getTriggerUserId() != null) {
                m.put("triggerUserName", userNames.computeIfAbsent(r.getTriggerUserId(), uid -> {
                    User u = userMapper.selectById(uid);
                    return u != null ? u.getNickname() : null;
                }));
            }
            return m;
        }).toList();
        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setRecords(rows);
        pr.setTotal(p.getTotal());
        pr.setPage(p.getCurrent());
        pr.setSize(p.getSize());
        return pr;
    }
}
