package com.devfusion.pipeline;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.devfusion.pipeline.mapper.PipelineJobMapper;
import com.devfusion.pipeline.mapper.PipelineMapper;
import com.devfusion.pipeline.mapper.PipelineStageMapper;

@RestController
public class PipelineController {

    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final ApplicationMapper applicationMapper;
    private final com.devfusion.pipeline.mapper.PipelineVariableMapper variableMapper;

    public PipelineController(PipelineMapper pipelineMapper, PipelineStageMapper stageMapper,
                              PipelineJobMapper jobMapper, ApplicationMapper applicationMapper,
                              com.devfusion.pipeline.mapper.PipelineVariableMapper variableMapper) {
        this.pipelineMapper = pipelineMapper;
        this.stageMapper = stageMapper;
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
        this.variableMapper = variableMapper;
    }

    public static final List<String> JOB_TYPES = List.of(
            "SHELL", "MAVEN_BUILD", "NPM_BUILD", "DOCKER_BUILD", "UNIT_TEST", "ARTIFACT_UPLOAD",
            "APPROVAL", "TRIGGER_PIPELINE");

    @Data
    public static class JobReq {
        private String name;
        private String type;
        private String config;
        private Integer continueOnFail;
        private Integer parallel;
        private Integer timeoutMinutes;
    }

    @Data
    public static class StageReq {
        private String name;
        private List<JobReq> jobs;
    }

    @Data
    public static class PipelineReq {
        private String name;
        private String description;
        private String triggerType;
        private String cron;
        private String status;
        private List<StageReq> stages;
    }

    @GetMapping("/api/applications/{appId}/pipelines")
    public Result<List<Map<String, Object>>> list(@PathVariable Long appId) {
        getApplication(appId);
        List<Pipeline> pipelines = pipelineMapper.selectList(Wrappers.<Pipeline>lambdaQuery()
                .eq(Pipeline::getApplicationId, appId).orderByDesc(Pipeline::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pipeline p : pipelines) {
            Map<String, Object> m = toVo(p);
            m.put("stageCount", stageMapper.selectCount(Wrappers.<PipelineStage>lambdaQuery()
                    .eq(PipelineStage::getPipelineId, p.getId())));
            m.put("lastRunStatus", null);
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping("/api/applications/{appId}/pipelines")
    @Transactional
    public Result<Pipeline> create(@PathVariable Long appId, @RequestBody PipelineReq req) {
        getApplication(appId);
        Pipeline p = new Pipeline();
        p.setApplicationId(appId);
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setTriggerType(req.getTriggerType() == null ? "MANUAL" : req.getTriggerType());
        p.setStatus("ENABLED");
        p.setWebhookToken(UUID.randomUUID().toString().replace("-", ""));
        p.setCreatedBy(CurrentUser.id());
        pipelineMapper.insert(p);
        saveStages(p.getId(), req.getStages());
        return Result.ok(p);
    }

    @GetMapping("/api/pipelines/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Pipeline p = getOrThrow(id);
        Map<String, Object> m = toVo(p);
        m.put("stages", stagesOf(id));
        return Result.ok(m);
    }

    /** 全量替换式保存编排 */
    @PutMapping("/api/pipelines/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody PipelineReq req) {
        Pipeline p = getOrThrow(id);
        if (req.getName() != null) {
            p.setName(req.getName());
        }
        if (req.getDescription() != null) {
            p.setDescription(req.getDescription());
        }
        if (req.getTriggerType() != null) {
            p.setTriggerType(req.getTriggerType());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        if (req.getCron() != null) {
            p.setCron(req.getCron());
        }
        pipelineMapper.updateById(p);
        if (req.getStages() != null) {
            saveStages(id, req.getStages());
        }
        return Result.ok();
    }

    @DeleteMapping("/api/pipelines/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        Pipeline p = getOrThrow(id);
        List<PipelineStage> stages = stageMapper.selectList(Wrappers.<PipelineStage>lambdaQuery()
                .eq(PipelineStage::getPipelineId, id));
        for (PipelineStage s : stages) {
            jobMapper.delete(Wrappers.<PipelineJob>lambdaQuery().eq(PipelineJob::getStageId, s.getId()));
        }
        stageMapper.delete(Wrappers.<PipelineStage>lambdaQuery().eq(PipelineStage::getPipelineId, id));
        pipelineMapper.deleteById(p.getId());
        return Result.ok();
    }

    // ---------- 流水线参数变量 ----------

    @Data
    public static class VariableReq {
        private String name;
        private String defaultValue;
        private Integer secret;
        private Integer runtimeEditable;
    }

    @GetMapping("/api/pipelines/{id}/variables")
    public Result<List<Map<String, Object>>> listVariables(@PathVariable Long id) {
        getOrThrow(id);
        List<PipelineVariable> vars = variableMapper.selectList(Wrappers.<PipelineVariable>lambdaQuery()
                .eq(PipelineVariable::getPipelineId, id).orderByAsc(PipelineVariable::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PipelineVariable v : vars) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("name", v.getName());
            // 密文不回传默认值
            m.put("defaultValue", v.getSecret() != null && v.getSecret() == 1 ? null : v.getDefaultValue());
            m.put("secret", v.getSecret());
            m.put("runtimeEditable", v.getRuntimeEditable());
            rows.add(m);
        }
        return Result.ok(rows);
    }

    @PostMapping("/api/pipelines/{id}/variables")
    public Result<PipelineVariable> createVariable(@PathVariable Long id, @RequestBody VariableReq req) {
        getOrThrow(id);
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ApiException("变量名不能为空");
        }
        Long exists = variableMapper.selectCount(Wrappers.<PipelineVariable>lambdaQuery()
                .eq(PipelineVariable::getPipelineId, id).eq(PipelineVariable::getName, req.getName()));
        if (exists > 0) {
            throw new ApiException("变量名已存在: " + req.getName());
        }
        PipelineVariable v = new PipelineVariable();
        v.setPipelineId(id);
        v.setName(req.getName());
        v.setDefaultValue(req.getDefaultValue());
        v.setSecret(req.getSecret() == null ? 0 : req.getSecret());
        v.setRuntimeEditable(req.getRuntimeEditable() == null ? 1 : req.getRuntimeEditable());
        variableMapper.insert(v);
        return Result.ok(v);
    }

    @DeleteMapping("/api/pipelines/{id}/variables/{varId}")
    public Result<Void> deleteVariable(@PathVariable Long id, @PathVariable Long varId) {
        getOrThrow(id);
        variableMapper.deleteById(varId);
        return Result.ok();
    }

    // ---------- 内部 ----------

    private void saveStages(Long pipelineId, List<StageReq> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new ApiException("流水线至少需要一个阶段");
        }
        for (StageReq sr : stages) {
            if (sr.getJobs() == null || sr.getJobs().isEmpty()) {
                throw new ApiException("阶段「" + sr.getName() + "」至少需要一个任务");
            }
            for (JobReq jr : sr.getJobs()) {
                if (!JOB_TYPES.contains(jr.getType())) {
                    throw new ApiException("不支持的任务类型: " + jr.getType());
                }
            }
        }
        stageMapper.delete(Wrappers.<PipelineStage>lambdaQuery().eq(PipelineStage::getPipelineId, pipelineId));
        int sort = 1;
        for (StageReq sr : stages) {
            PipelineStage s = new PipelineStage();
            s.setPipelineId(pipelineId);
            s.setName(sr.getName());
            s.setSort(sort++);
            stageMapper.insert(s);
            int jobSort = 1;
            for (JobReq jr : sr.getJobs()) {
                PipelineJob j = new PipelineJob();
                j.setStageId(s.getId());
                j.setName(jr.getName());
                j.setType(jr.getType());
                j.setConfig(jr.getConfig());
                j.setContinueOnFail(jr.getContinueOnFail() == null ? 0 : jr.getContinueOnFail());
                j.setParallel(jr.getParallel() == null ? 0 : jr.getParallel());
                j.setTimeoutMinutes(jr.getTimeoutMinutes());
                j.setSort(jobSort++);
                jobMapper.insert(j);
            }
        }
    }

    private List<Map<String, Object>> stagesOf(Long pipelineId) {
        List<PipelineStage> stages = stageMapper.selectList(Wrappers.<PipelineStage>lambdaQuery()
                .eq(PipelineStage::getPipelineId, pipelineId).orderByAsc(PipelineStage::getSort));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PipelineStage s : stages) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("sort", s.getSort());
            m.put("jobs", jobMapper.selectList(Wrappers.<PipelineJob>lambdaQuery()
                            .eq(PipelineJob::getStageId, s.getId()).orderByAsc(PipelineJob::getSort))
                    .stream().map(j -> {
                        Map<String, Object> jm = new HashMap<>();
                        jm.put("id", j.getId());
                        jm.put("name", j.getName());
                        jm.put("type", j.getType());
                        jm.put("config", j.getConfig());
                        jm.put("continueOnFail", j.getContinueOnFail());
                        jm.put("parallel", j.getParallel());
                        jm.put("timeoutMinutes", j.getTimeoutMinutes());
                        jm.put("sort", j.getSort());
                        return jm;
                    }).toList());
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> toVo(Pipeline p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("applicationId", p.getApplicationId());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("triggerType", p.getTriggerType());
        m.put("cron", p.getCron());
        m.put("webhookToken", p.getWebhookToken());
        m.put("status", p.getStatus());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    private Pipeline getOrThrow(Long id) {
        Pipeline p = pipelineMapper.selectById(id);
        if (p == null) {
            throw ApiException.notFound("流水线");
        }
        return p;
    }

    private Application getApplication(Long id) {
        Application app = applicationMapper.selectById(id);
        if (app == null) {
            throw ApiException.notFound("应用");
        }
        return app;
    }
}
