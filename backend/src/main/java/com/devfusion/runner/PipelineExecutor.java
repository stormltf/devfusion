package com.devfusion.runner;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.artifact.Artifact;
import com.devfusion.artifact.mapper.ArtifactMapper;
import com.devfusion.notification.NotificationService;
import com.devfusion.pipeline.PipelineStage;
import com.devfusion.pipeline.mapper.PipelineStageMapper;
import com.devfusion.runner.mapper.PipelineRunMapper;
import com.devfusion.runner.mapper.RunJobMapper;
import com.devfusion.runner.mapper.RunStageMapper;
import com.devfusion.runner.mapper.RunVariableMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 流水线执行引擎：stage 之间串行；stage 内 job 默认串行，parallel=1 的相邻 job 并发执行。
 * job 配置在触发时复制为 run 快照（RunStage/RunJob），编辑流水线不影响进行中的 run。
 */
@Slf4j
@Component
public class PipelineExecutor {

    public static final String CANCEL_KEY_PREFIX = "df:cancel:run:";

    private final PipelineRunMapper runMapper;
    private final RunStageMapper runStageMapper;
    private final RunJobMapper runJobMapper;
    private final RunVariableMapper runVariableMapper;
    private final PipelineStageMapper stageMapper;
    private final ApplicationMapper applicationMapper;
    private final ArtifactMapper artifactMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final RunService runService;
    private final StringRedisTemplate redis;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 审批挂起的 job：等待 latch；decision 记录审批结果 */
    private final Map<Long, CountDownLatch> approvalLatches = new ConcurrentHashMap<>();
    private final Map<Long, String> approvalDecisions = new ConcurrentHashMap<>();

    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    public PipelineExecutor(PipelineRunMapper runMapper, RunStageMapper runStageMapper,
                            RunJobMapper runJobMapper, RunVariableMapper runVariableMapper,
                            PipelineStageMapper stageMapper, ApplicationMapper applicationMapper,
                            ArtifactMapper artifactMapper, UserMapper userMapper,
                            NotificationService notificationService, @Lazy RunService runService,
                            StringRedisTemplate redis) {
        this.runMapper = runMapper;
        this.runStageMapper = runStageMapper;
        this.runJobMapper = runJobMapper;
        this.runVariableMapper = runVariableMapper;
        this.stageMapper = stageMapper;
        this.applicationMapper = applicationMapper;
        this.artifactMapper = artifactMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.runService = runService;
        this.redis = redis;
    }

    public void submitAsync(Long runId) {
        pool.submit(() -> {
            try {
                execute(runId);
            } catch (Exception e) {
                log.error("pipeline run {} crashed", runId, e);
                PipelineRun run = runMapper.selectById(runId);
                if (run != null && ("RUNNING".equals(run.getStatus()) || "WAITING_APPROVAL".equals(run.getStatus()))) {
                    run.setStatus("FAILED");
                    run.setFinishedAt(LocalDateTime.now());
                    runMapper.updateById(run);
                }
            }
        });
    }

    private enum StageResult { OK, BLOCK_FAIL, CANCELED }

    private void execute(Long runId) {
        PipelineRun run = runMapper.selectById(runId);
        if (run == null) {
            return;
        }
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        runMapper.updateById(run);
        redis.delete(CANCEL_KEY_PREFIX + runId);

        Map<String, String> vars = new HashMap<>();
        Map<String, Boolean> secret = new HashMap<>();
        for (RunVariable rv : runVariableMapper.selectList(Wrappers.<RunVariable>lambdaQuery()
                .eq(RunVariable::getRunId, runId))) {
            vars.put(rv.getName(), rv.getValue());
            secret.put(rv.getName(), rv.getSecret() != null && rv.getSecret() == 1);
        }

        List<RunStage> stages = runStageMapper.selectList(Wrappers.<RunStage>lambdaQuery()
                .eq(RunStage::getRunId, runId).orderByAsc(RunStage::getSort));
        boolean hasDocker = false;
        String resultStatus = "SUCCESS";
        String failedReason = null;

        outer:
        for (RunStage stage : stages) {
            if (isCanceled(runId)) {
                resultStatus = "CANCELED";
                break;
            }
            stage.setStatus("RUNNING");
            stage.setStartedAt(LocalDateTime.now());
            runStageMapper.updateById(stage);

            List<RunJob> jobs = runJobMapper.selectList(Wrappers.<RunJob>lambdaQuery()
                    .eq(RunJob::getRunStageId, stage.getId()).orderByAsc(RunJob::getId));
            for (RunJob j : jobs) {
                if (j.getType() != null && j.getType().equals("DOCKER_BUILD")) {
                    hasDocker = true;
                }
            }

            StageResult sr = runStageJobs(stage, jobs, runId, vars, secret);
            switch (sr) {
                case CANCELED -> {
                    stage.setStatus("CANCELED");
                    stage.setFinishedAt(LocalDateTime.now());
                    runStageMapper.updateById(stage);
                    resultStatus = "CANCELED";
                    break outer;
                }
                case BLOCK_FAIL -> {
                    stage.setStatus("FAILED");
                    stage.setFinishedAt(LocalDateTime.now());
                    runStageMapper.updateById(stage);
                    resultStatus = "FAILED";
                    RunJob last = jobs.get(jobs.size() - 1);
                    failedReason = stage.getName() + " / " + last.getName();
                    break outer;
                }
                case OK -> {
                    stage.setStatus("SUCCESS");
                    stage.setFinishedAt(LocalDateTime.now());
                    runStageMapper.updateById(stage);
                }
            }
        }

        // 失败/取消后，未执行的 stage 与 job 标记 SKIPPED，卡在 RUNNING 的标记 CANCELED
        if (!"SUCCESS".equals(resultStatus)) {
            for (RunStage s : stages) {
                if ("PENDING".equals(s.getStatus())) {
                    s.setStatus("SKIPPED");
                    runStageMapper.updateById(s);
                } else if ("RUNNING".equals(s.getStatus()) && !"FAILED".equals(s.getStatus())) {
                    s.setStatus("CANCELED");
                    s.setFinishedAt(LocalDateTime.now());
                    runStageMapper.updateById(s);
                }
                runJobMapper.selectList(Wrappers.<RunJob>lambdaQuery().eq(RunJob::getRunStageId, s.getId()))
                        .stream().filter(j -> "PENDING".equals(j.getStatus()))
                        .forEach(j -> markJobAndRest(j, "SKIPPED"));
            }
        }

        run.setStatus(resultStatus);
        run.setFinishedAt(LocalDateTime.now());
        if (run.getStartedAt() != null) {
            run.setDurationMs(Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
        }
        runMapper.updateById(run);

        if ("SUCCESS".equals(resultStatus)) {
            createArtifact(run, hasDocker);
        } else if ("FAILED".equals(resultStatus) && run.getTriggerUserId() != null) {
            notificationService.send(run.getTriggerUserId(), "BUILD_FAILED",
                    "构建失败", "流水线 #" + runId + "（" + run.getVersion() + "）执行失败",
                    "/runs/" + runId);
        }
        log.info("run {} finished: {} {}", runId, resultStatus, failedReason == null ? "" : "at " + failedReason);
    }

    /** 执行一个 stage 的全部 job：串行单元 + parallel 批并发；返回 stage 结果 */
    private StageResult runStageJobs(RunStage stage, List<RunJob> jobs, Long runId,
                                     Map<String, String> vars, Map<String, Boolean> secret) {
        int i = 0;
        while (i < jobs.size()) {
            RunJob cur = jobs.get(i);
            if (isCanceled(runId)) {
                return StageResult.CANCELED;
            }
            boolean parallel = cur.getParallel() != null && cur.getParallel() == 1
                    && !"APPROVAL".equals(cur.getType()) && !"TRIGGER_PIPELINE".equals(cur.getType());
            if (!parallel) {
                boolean ok = runJob(cur, runId, vars, secret);
                i++;
                if (!ok && cur.getContinueOnFail() == 0) {
                    return isCanceled(runId) ? StageResult.CANCELED : StageResult.BLOCK_FAIL;
                }
                continue;
            }
            // 收集相邻 parallel job 为一批并发执行
            List<RunJob> batch = new ArrayList<>();
            while (i < jobs.size()) {
                RunJob j = jobs.get(i);
                if (j.getParallel() == null || j.getParallel() != 1
                        || "APPROVAL".equals(j.getType()) || "TRIGGER_PIPELINE".equals(j.getType())) {
                    break;
                }
                batch.add(j);
                i++;
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (RunJob j : batch) {
                futures.add(pool.submit(() -> runJob(j, runId, vars, secret)));
            }
            boolean anyBlockingFail = false;
            for (int k = 0; k < batch.size(); k++) {
                boolean ok;
                try {
                    ok = futures.get(k).get();
                } catch (Exception e) {
                    ok = false;
                }
                RunJob j = batch.get(k);
                if (!ok && j.getContinueOnFail() == 0) {
                    anyBlockingFail = true;
                }
            }
            if (isCanceled(runId)) {
                return StageResult.CANCELED;
            }
            if (anyBlockingFail) {
                return StageResult.BLOCK_FAIL;
            }
        }
        return StageResult.OK;
    }

    /** 执行单个 job：流式写日志（每 3 行刷新一次），返回是否成功 */
    private boolean runJob(RunJob job, Long runId, Map<String, String> vars, Map<String, Boolean> secret) {
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        job.setLog("");
        runJobMapper.updateById(job);

        if ("APPROVAL".equals(job.getType())) {
            return waitApproval(job, runId);
        }
        if ("TRIGGER_PIPELINE".equals(job.getType())) {
            return triggerChild(job, runId);
        }

        long budgetMs = timeoutBudget(job);
        long startMs = System.currentTimeMillis();

        JobLogSimulator.Script script = JobLogSimulator.simulate(job, vars, secret);
        List<String> all = new ArrayList<>();
        all.addAll(JobLogSimulator.envHeader(vars, secret));
        all.addAll(script.lines());

        StringBuilder sb = new StringBuilder();
        int i = 0;
        boolean timedOut = false;
        for (String line : all) {
            if (isCanceled(runId)) {
                markJobAndRest(job, "CANCELED");
                return false;
            }
            if (System.currentTimeMillis() - startMs > budgetMs) {
                timedOut = true;
                break;
            }
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append(line).append("\n");
            if (++i % 3 == 0) {
                job.setLog(sb.toString());
                runJobMapper.updateById(job);
            }
            if (!sleep(120, runId)) {
                markJobAndRest(job, "CANCELED");
                return false;
            }
        }
        if (timedOut) {
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[error] 任务超时（超过 ").append(job.getTimeoutMinutes()).append(" 分钟限制），判定失败\n");
            job.setLog(sb.toString());
            job.setStatus("FAILED");
            job.setFinishedAt(LocalDateTime.now());
            runJobMapper.updateById(job);
            return false;
        }
        // 模拟长耗时任务（config.simulateSeconds），期间仍响应取消/超时
        if (script.simulateSeconds() > 0) {
            long waited = 0;
            while (waited < script.simulateSeconds() * 1000L) {
                if (isCanceled(runId)) {
                    job.setLog(sb.toString());
                    markJobAndRest(job, "CANCELED");
                    return false;
                }
                if (System.currentTimeMillis() - startMs > budgetMs) {
                    sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[error] 任务超时，判定失败\n");
                    job.setLog(sb.toString());
                    job.setStatus("FAILED");
                    job.setFinishedAt(LocalDateTime.now());
                    runJobMapper.updateById(job);
                    return false;
                }
                if (!sleep(300, runId)) {
                    job.setLog(sb.toString());
                    markJobAndRest(job, "CANCELED");
                    return false;
                }
                waited += 300;
            }
        }
        job.setLog(sb.toString());
        job.setStatus(script.fail() ? "FAILED" : "SUCCESS");
        job.setFinishedAt(LocalDateTime.now());
        runJobMapper.updateById(job);
        return !script.fail();
    }

    private long timeoutBudget(RunJob job) {
        long budget = Long.MAX_VALUE;
        if (job.getTimeoutMinutes() != null && job.getTimeoutMinutes() > 0) {
            budget = job.getTimeoutMinutes() * 60_000L;
        }
        String cfg = job.getConfig();
        if (cfg != null && cfg.contains("simulateTimeoutSeconds")) {
            try {
                JsonNode c = JSON.readTree(cfg);
                if (c.hasNonNull("simulateTimeoutSeconds")) {
                    budget = c.get("simulateTimeoutSeconds").asLong() * 1000L;
                }
            } catch (Exception ignored) {
            }
        }
        return budget;
    }

    /** 人工审批：run 进入 WAITING_APPROVAL，挂起等待 approve/reject，取消优先 */
    private boolean waitApproval(RunJob job, Long runId) {
        job.setStatus("WAITING_APPROVAL");
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[info] 任务「").append(job.getName()).append("」等待人工审批...\n");
        job.setLog(sb.toString());
        runJobMapper.updateById(job);

        PipelineRun run = runMapper.selectById(runId);
        if (run != null) {
            run.setStatus("WAITING_APPROVAL");
            runMapper.updateById(run);
        }
        // 通知系统管理员（审批人规则：系统管理员/项目 OWNER/ADMIN，此处取系统管理员）
        List<Long> admins = userMapper.selectList(Wrappers.<User>lambdaQuery().eq(User::getRole, "ADMIN"))
                .stream().map(User::getId).toList();
        notificationService.sendToEach(admins, "APPROVAL_PENDING",
                "流水线待审批", "构建 #" + runId + " 任务「" + job.getName() + "」等待审批",
                "/runs/" + runId);

        CountDownLatch latch = new CountDownLatch(1);
        approvalLatches.put(job.getId(), latch);
        String decision = null;
        while (decision == null) {
            if (isCanceled(runId)) {
                break;
            }
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            decision = approvalDecisions.get(job.getId());
        }
        approvalLatches.remove(job.getId());
        approvalDecisions.remove(job.getId());

        if (isCanceled(runId) || (decision == null)) {
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[info] 审批等待被取消\n");
            job.setLog(sb.toString());
            markJobAndRest(job, "CANCELED");
            return false;
        }
        if ("REJECTED".equals(decision)) {
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[error] 审批被拒绝，任务失败\n");
            job.setLog(sb.toString());
            job.setStatus("FAILED");
            job.setFinishedAt(LocalDateTime.now());
            runJobMapper.updateById(job);
            return false;
        }
        sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[info] 审批通过，继续执行\n");
        job.setLog(sb.toString());
        job.setStatus("SUCCESS");
        job.setFinishedAt(LocalDateTime.now());
        runJobMapper.updateById(job);
        return true;
    }

    /** 子流水线触发：config.pipelineId，config.wait（默认 true）等待结果 */
    private boolean triggerChild(RunJob job, Long runId) {
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode c = JSON.readTree(job.getConfig() == null ? "{}" : job.getConfig());
            Long childId = c.path("pipelineId").asLong(0);
            boolean wait = !c.has("wait") || c.get("wait").asBoolean(true);
            if (childId == 0 || childId.equals(runMapper.selectById(runId).getPipelineId())) {
                sb.append("[error] 子流水线未配置或指向自身\n");
                job.setLog(sb.toString());
                job.setStatus("FAILED");
                job.setFinishedAt(LocalDateTime.now());
                runJobMapper.updateById(job);
                return false;
            }
            PipelineRun parent = runMapper.selectById(runId);
            PipelineRun child = runService.trigger(childId, "TRIGGER", parent.getTriggerUserId());
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[info] 已触发子流水线 #").append(childId).append("，构建号 #").append(child.getId()).append("\n");
            job.setLog(sb.toString());
            runJobMapper.updateById(job);
            if (!wait) {
                job.setStatus("SUCCESS");
                job.setFinishedAt(LocalDateTime.now());
                runJobMapper.updateById(job);
                return true;
            }
            String status = child.getStatus();
            long deadline = System.currentTimeMillis() + 60_000L;
            while (!List.of("SUCCESS", "FAILED", "CANCELED").contains(status) && System.currentTimeMillis() < deadline) {
                if (!sleep(500, runId)) {
                    return false;
                }
                status = runMapper.selectById(child.getId()).getStatus();
            }
            sb.append("[").append(JobLogSimulator.ts()).append("] ").append("[info] 子流水线结果: ").append(status).append("\n");
            job.setLog(sb.toString());
            boolean ok = "SUCCESS".equals(status);
            job.setStatus(ok ? "SUCCESS" : "FAILED");
            job.setFinishedAt(LocalDateTime.now());
            runJobMapper.updateById(job);
            return ok;
        } catch (Exception e) {
            sb.append("[error] 子流水线触发失败: ").append(e.getMessage()).append("\n");
            job.setLog(sb.toString());
            job.setStatus("FAILED");
            job.setFinishedAt(LocalDateTime.now());
            runJobMapper.updateById(job);
            return false;
        }
    }

    /** 审批回调（来自 HTTP 接口）：记录决策并唤醒挂起线程 */
    public void onApprovalDecision(Long jobId, boolean approved) {
        approvalDecisions.put(jobId, approved ? "APPROVED" : "REJECTED");
        CountDownLatch latch = approvalLatches.get(jobId);
        if (latch != null) {
            latch.countDown();
        }
    }

    private boolean sleep(long ms, Long runId) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void markJobAndRest(RunJob job, String status) {
        job.setStatus(status);
        job.setFinishedAt(LocalDateTime.now());
        runJobMapper.updateById(job);
    }

    /** 构建成功后生成制品，版本协议参考简单云：{应用KEY}-{yyyyMMddHHmm}-{runId} */
    private void createArtifact(PipelineRun run, boolean hasDocker) {
        Application app = applicationMapper.selectById(run.getApplicationId());
        if (app == null) {
            return;
        }
        Artifact artifact = new Artifact();
        artifact.setApplicationId(app.getId());
        artifact.setVersion(app.getKey() + "-" + java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMddHHmm").format(run.getFinishedAt()) + "-" + run.getId());
        artifact.setPipelineRunId(run.getId());
        artifact.setType(hasDocker ? "IMAGE" : "JAR");
        artifact.setStage("SNAPSHOT");
        artifact.setPath("registry.devfusion.local/" + app.getKey().toLowerCase() + "/" + artifact.getVersion());
        artifact.setSizeBytes(30_000_000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(20_000_000));
        artifactMapper.insert(artifact);
    }

    private boolean isCanceled(Long runId) {
        Boolean exists = redis.hasKey(CANCEL_KEY_PREFIX + runId);
        return exists != null && exists;
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}
