package com.devfusion.runner;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.common.ApiException;
import com.devfusion.pipeline.Pipeline;
import com.devfusion.pipeline.PipelineJob;
import com.devfusion.pipeline.PipelineStage;
import com.devfusion.pipeline.PipelineVariable;
import com.devfusion.pipeline.mapper.PipelineJobMapper;
import com.devfusion.pipeline.mapper.PipelineMapper;
import com.devfusion.pipeline.mapper.PipelineStageMapper;
import com.devfusion.pipeline.mapper.PipelineVariableMapper;
import com.devfusion.runner.mapper.PipelineRunMapper;
import com.devfusion.runner.mapper.RunJobMapper;
import com.devfusion.runner.mapper.RunStageMapper;
import com.devfusion.runner.mapper.RunVariableMapper;
import com.devfusion.runner.mapper.RunWorkItemMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.mapper.WorkItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RunService {

    /** 工作项编号，如 GWP-STORY-1 */
    private static final Pattern ITEM_NO_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]+-[A-Z]+-\\d+\\b");

    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final ApplicationMapper applicationMapper;
    private final PipelineRunMapper runMapper;
    private final RunStageMapper runStageMapper;
    private final RunJobMapper runJobMapper;
    private final PipelineVariableMapper variableMapper;
    private final RunVariableMapper runVariableMapper;
    private final RunWorkItemMapper runWorkItemMapper;
    private final WorkItemMapper workItemMapper;
    private final UserMapper userMapper;
    private final PipelineExecutor executor;
    private final StringRedisTemplate redis;

    public RunService(PipelineMapper pipelineMapper, PipelineStageMapper stageMapper,
                      PipelineJobMapper jobMapper, ApplicationMapper applicationMapper,
                      PipelineRunMapper runMapper, RunStageMapper runStageMapper,
                      RunJobMapper runJobMapper, PipelineVariableMapper variableMapper,
                      RunVariableMapper runVariableMapper, RunWorkItemMapper runWorkItemMapper,
                      WorkItemMapper workItemMapper, UserMapper userMapper,
                      PipelineExecutor executor, StringRedisTemplate redis) {
        this.pipelineMapper = pipelineMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
        this.runMapper = runMapper;
        this.runStageMapper = runStageMapper;
        this.runJobMapper = runJobMapper;
        this.variableMapper = variableMapper;
        this.runVariableMapper = runVariableMapper;
        this.runWorkItemMapper = runWorkItemMapper;
        this.workItemMapper = workItemMapper;
        this.userMapper = userMapper;
        this.executor = executor;
        this.redis = redis;
    }

    @Transactional
    public PipelineRun trigger(Long pipelineId, String triggerType, Long userId) {
        return trigger(pipelineId, triggerType, userId, null, null, null);
    }

    @Transactional
    public PipelineRun trigger(Long pipelineId, String triggerType, Long userId,
                               Map<String, String> overrides, String branch, String commitMessage) {
        Pipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw ApiException.notFound("流水线");
        }
        if (!"ENABLED".equals(pipeline.getStatus())) {
            throw new ApiException("流水线已停用");
        }
        List<PipelineStage> stages = stageMapper.selectList(Wrappers.<PipelineStage>lambdaQuery()
                .eq(PipelineStage::getPipelineId, pipelineId).orderByAsc(PipelineStage::getSort));
        if (stages.isEmpty()) {
            throw new ApiException("流水线尚未配置阶段");
        }
        Application app = applicationMapper.selectById(pipeline.getApplicationId());
        if (app == null) {
            throw ApiException.notFound("应用");
        }

        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setApplicationId(app.getId());
        run.setTriggerType(triggerType);
        run.setTriggerUserId(userId);
        run.setStatus("PENDING");
        runMapper.insert(run);
        run.setVersion(app.getKey() + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .format(LocalDateTime.now()) + "-" + run.getId());
        runMapper.updateById(run);

        // 复制 stage/job 为运行时快照
        for (PipelineStage stage : stages) {
            RunStage rs = new RunStage();
            rs.setRunId(run.getId());
            rs.setStageId(stage.getId());
            rs.setName(stage.getName());
            rs.setSort(stage.getSort());
            rs.setStatus("PENDING");
            runStageMapper.insert(rs);
            for (PipelineJob job : jobMapper.selectList(Wrappers.<PipelineJob>lambdaQuery()
                    .eq(PipelineJob::getStageId, stage.getId()).orderByAsc(PipelineJob::getSort))) {
                RunJob rj = new RunJob();
                rj.setRunId(run.getId());
                rj.setRunStageId(rs.getId());
                rj.setJobId(job.getId());
                rj.setName(job.getName());
                rj.setType(job.getType());
                rj.setConfig(job.getConfig());
                rj.setContinueOnFail(job.getContinueOnFail());
                rj.setParallel(job.getParallel());
                rj.setTimeoutMinutes(job.getTimeoutMinutes());
                rj.setStatus("PENDING");
                runJobMapper.insert(rj);
            }
        }

        saveVariables(run, pipeline, app, userId, triggerType, branch, overrides);
        linkWorkItems(run, commitMessage);

        // 事务提交后再交给执行引擎，避免引擎线程读不到未提交的 run
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executor.submitAsync(run.getId());
                    }
                });
        return run;
    }

    /** 合并系统变量与用户变量（默认值 + 触发覆盖），落 run_variable 快照 */
    private void saveVariables(PipelineRun run, Pipeline pipeline, Application app,
                               Long userId, String triggerType, String branch,
                               Map<String, String> overrides) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Map<String, Integer> secretFlag = new LinkedHashMap<>();

        // 系统变量（约 8 个）
        String triggerUser = "system";
        if (userId != null) {
            User u = userMapper.selectById(userId);
            triggerUser = u != null ? u.getNickname() : String.valueOf(userId);
        }
        resolved.put("PIPELINE_ID", String.valueOf(pipeline.getId()));
        resolved.put("PIPELINE_NAME", pipeline.getName());
        resolved.put("BUILD_NUMBER", String.valueOf(run.getId()));
        resolved.put("BUILD_VERSION", run.getVersion());
        resolved.put("BRANCH", branch == null || branch.isBlank() ? "master" : branch);
        resolved.put("TRIGGER_TYPE", triggerType);
        resolved.put("TRIGGER_USER", triggerUser);
        resolved.put("APP_KEY", app.getKey());
        secretFlag.put("PIPELINE_ID", 0);
        secretFlag.put("PIPELINE_NAME", 0);
        secretFlag.put("BUILD_NUMBER", 0);
        secretFlag.put("BUILD_VERSION", 0);
        secretFlag.put("BRANCH", 0);
        secretFlag.put("TRIGGER_TYPE", 0);
        secretFlag.put("TRIGGER_USER", 0);
        secretFlag.put("APP_KEY", 0);

        // 用户静态变量默认值
        for (PipelineVariable v : variableMapper.selectList(Wrappers.<PipelineVariable>lambdaQuery()
                .eq(PipelineVariable::getPipelineId, pipeline.getId()))) {
            resolved.put(v.getName(), v.getDefaultValue());
            secretFlag.put(v.getName(), v.getSecret());
        }
        // 触发时覆盖（仅运行时可改的变量允许覆盖）
        if (overrides != null) {
            for (PipelineVariable v : variableMapper.selectList(Wrappers.<PipelineVariable>lambdaQuery()
                    .eq(PipelineVariable::getPipelineId, pipeline.getId()))) {
                if (overrides.containsKey(v.getName())
                        && (v.getRuntimeEditable() == null || v.getRuntimeEditable() == 1)) {
                    resolved.put(v.getName(), overrides.get(v.getName()));
                }
            }
            // 额外传入、但流水线未声明的变量也允许注入（密文未知则按明文记录）
            for (Map.Entry<String, String> e : overrides.entrySet()) {
                resolved.putIfAbsent(e.getKey(), e.getValue());
                secretFlag.putIfAbsent(e.getKey(), 0);
            }
        }

        for (Map.Entry<String, String> e : resolved.entrySet()) {
            RunVariable rv = new RunVariable();
            rv.setRunId(run.getId());
            rv.setName(e.getKey());
            rv.setValue(e.getValue());
            rv.setSecret(secretFlag.getOrDefault(e.getKey(), 0));
            runVariableMapper.insert(rv);
        }
    }

    /** 解析 commitMessage / 关联编号中的工作项编号，自动关联到本次构建 */
    private void linkWorkItems(PipelineRun run, String commitMessage) {
        if (commitMessage == null || commitMessage.isBlank()) {
            return;
        }
        Matcher m = ITEM_NO_PATTERN.matcher(commitMessage);
        while (m.find()) {
            String no = m.group();
            WorkItem item = workItemMapper.selectOne(Wrappers.<WorkItem>lambdaQuery()
                    .eq(WorkItem::getItemNo, no).last("LIMIT 1"));
            if (item == null) {
                continue;
            }
            Long exists = runWorkItemMapper.selectCount(Wrappers.<RunWorkItem>lambdaQuery()
                    .eq(RunWorkItem::getRunId, run.getId())
                    .eq(RunWorkItem::getWorkItemId, item.getId()));
            if (exists > 0) {
                continue;
            }
            RunWorkItem rwi = new RunWorkItem();
            rwi.setRunId(run.getId());
            rwi.setWorkItemId(item.getId());
            rwi.setWorkItemNo(item.getItemNo());
            runWorkItemMapper.insert(rwi);
        }
    }
}
