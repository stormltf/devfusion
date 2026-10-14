package com.devfusion.testplan;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.project.Project;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.testcase.TestCase;
import com.devfusion.testcase.mapper.TestCaseMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.testplan.mapper.TestPlanCaseMapper;
import com.devfusion.testplan.mapper.TestPlanMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 测试计划：CRUD、加入/移除用例、设执行人、计划内执行（同步 test_case.latest_result）、进度统计 */
@RestController
public class TestPlanController {

    private static final Set<String> RESULTS = Set.of("PASS", "FAIL", "BLOCKED", "SKIPPED");

    private final TestPlanMapper planMapper;
    private final TestPlanCaseMapper planCaseMapper;
    private final TestCaseMapper caseMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;

    public TestPlanController(TestPlanMapper planMapper, TestPlanCaseMapper planCaseMapper,
                              TestCaseMapper caseMapper, ProjectMapper projectMapper, UserMapper userMapper) {
        this.planMapper = planMapper;
        this.planCaseMapper = planCaseMapper;
        this.caseMapper = caseMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
    }

    @Data
    public static class PlanReq {
        private String name;
        private Long ownerId;
        private String stage;
        private Long iterationId;
        private String status;
    }

    @Data
    public static class AddCasesReq {
        private List<Long> caseIds;
    }

    @Data
    public static class ExecutorReq {
        private List<Assignment> assignments;

        @Data
        public static class Assignment {
            private Long caseId;
            private Long executorId;
        }
    }

    @Data
    public static class ExecuteReq {
        private Long caseId;
        private String result;
        private String note;
        private Long bugItemId;
        private Long executorId;
    }

    @GetMapping("/api/projects/{projectId}/test-plans")
    public Result<List<Map<String, Object>>> list(@PathVariable Long projectId) {
        List<TestPlan> plans = planMapper.selectList(Wrappers.<TestPlan>lambdaQuery()
                .eq(TestPlan::getProjectId, projectId).orderByDesc(TestPlan::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TestPlan p : plans) {
            Map<String, Object> m = baseVo(p);
            m.putAll(progress(p.getId()));
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/api/projects/{projectId}/test-plans")
    @Transactional
    public Result<TestPlan> create(@PathVariable Long projectId, @RequestBody PlanReq req) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("项目");
        }
        TestPlan p = new TestPlan();
        p.setProjectId(projectId);
        p.setName(req.getName());
        p.setOwnerId(req.getOwnerId());
        p.setStage(req.getStage());
        p.setIterationId(req.getIterationId());
        p.setStatus(req.getStatus() == null ? "PLANNED" : req.getStatus());
        p.setCreatedBy(CurrentUser.id());
        planMapper.insert(p);
        return Result.ok(p);
    }

    @GetMapping("/api/test-plans/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        TestPlan p = getOrThrow(id);
        Map<String, Object> m = baseVo(p);
        m.putAll(progress(id));
        m.put("cases", caseRows(id));
        return Result.ok(m);
    }

    @PutMapping("/api/test-plans/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody PlanReq req) {
        TestPlan p = getOrThrow(id);
        if (req.getName() != null) {
            p.setName(req.getName());
        }
        if (req.getOwnerId() != null) {
            p.setOwnerId(req.getOwnerId());
        }
        if (req.getStage() != null) {
            p.setStage(req.getStage());
        }
        if (req.getIterationId() != null) {
            p.setIterationId(req.getIterationId());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        planMapper.updateById(p);
        return Result.ok();
    }

    @DeleteMapping("/api/test-plans/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        planCaseMapper.delete(Wrappers.<TestPlanCase>lambdaQuery().eq(TestPlanCase::getPlanId, id));
        planMapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping("/api/test-plans/{id}/cases")
    @Transactional
    public Result<Void> addCases(@PathVariable Long id, @RequestBody AddCasesReq req) {
        TestPlan p = getOrThrow(id);
        if (req.getCaseIds() == null) {
            return Result.ok();
        }
        for (Long caseId : req.getCaseIds()) {
            TestCase tc = caseMapper.selectById(caseId);
            if (tc == null || !tc.getProjectId().equals(p.getProjectId())) {
                continue;
            }
            Long exists = planCaseMapper.selectCount(Wrappers.<TestPlanCase>lambdaQuery()
                    .eq(TestPlanCase::getPlanId, id).eq(TestPlanCase::getCaseId, caseId));
            if (exists > 0) {
                continue;
            }
            TestPlanCase pc = new TestPlanCase();
            pc.setPlanId(id);
            pc.setCaseId(caseId);
            pc.setResult("UNEXECUTED");
            planCaseMapper.insert(pc);
        }
        return Result.ok();
    }

    @DeleteMapping("/api/test-plans/{id}/cases/{caseId}")
    @Transactional
    public Result<Void> removeCase(@PathVariable Long id, @PathVariable Long caseId) {
        getOrThrow(id);
        planCaseMapper.delete(Wrappers.<TestPlanCase>lambdaQuery()
                .eq(TestPlanCase::getPlanId, id).eq(TestPlanCase::getCaseId, caseId));
        return Result.ok();
    }

    @PutMapping("/api/test-plans/{id}/executors")
    @Transactional
    public Result<Void> setExecutors(@PathVariable Long id, @RequestBody ExecutorReq req) {
        getOrThrow(id);
        if (req.getAssignments() == null) {
            return Result.ok();
        }
        for (ExecutorReq.Assignment a : req.getAssignments()) {
            TestPlanCase pc = planCaseMapper.selectOne(Wrappers.<TestPlanCase>lambdaQuery()
                    .eq(TestPlanCase::getPlanId, id).eq(TestPlanCase::getCaseId, a.getCaseId()));
            if (pc == null) {
                continue;
            }
            pc.setExecutorId(a.getExecutorId());
            planCaseMapper.updateById(pc);
        }
        return Result.ok();
    }

    @PostMapping("/api/test-plans/{id}/execute")
    @Transactional
    public Result<Map<String, Object>> execute(@PathVariable Long id, @RequestBody ExecuteReq req) {
        TestPlan p = getOrThrow(id);
        if (!RESULTS.contains(req.getResult())) {
            throw new ApiException("result 必须是 PASS/FAIL/BLOCKED/SKIPPED");
        }
        TestCase tc = caseMapper.selectById(req.getCaseId());
        if (tc == null || !tc.getProjectId().equals(p.getProjectId())) {
            throw ApiException.notFound("测试用例");
        }
        TestPlanCase pc = planCaseMapper.selectOne(Wrappers.<TestPlanCase>lambdaQuery()
                .eq(TestPlanCase::getPlanId, id).eq(TestPlanCase::getCaseId, req.getCaseId()));
        if (pc == null) {
            pc = new TestPlanCase();
            pc.setPlanId(id);
            pc.setCaseId(req.getCaseId());
        }
        Long executor = req.getExecutorId() != null ? req.getExecutorId() : CurrentUser.id();
        pc.setExecutorId(executor);
        pc.setResult(req.getResult());
        pc.setNote(req.getNote());
        pc.setExecutedAt(LocalDateTime.now());
        if (pc.getId() == null) {
            planCaseMapper.insert(pc);
        } else {
            planCaseMapper.updateById(pc);
        }
        // 同步用例最新结果
        tc.setLatestResult(req.getResult());
        tc.setLatestExecutorId(executor);
        tc.setLatestExecutedAt(pc.getExecutedAt());
        if ("FAIL".equals(req.getResult())) {
            tc.setBugItemId(req.getBugItemId());
        }
        caseMapper.updateById(tc);
        Map<String, Object> m = new HashMap<>();
        m.put("caseId", req.getCaseId());
        m.put("result", req.getResult());
        m.put("executedAt", pc.getExecutedAt());
        return Result.ok(m);
    }

    /** 用例执行历史：该用例在各计划中的执行结果 */
    @GetMapping("/api/test-cases/{id}/executions")
    public Result<List<Map<String, Object>>> executions(@PathVariable Long id) {
        TestCase tc = caseMapper.selectById(id);
        if (tc == null) {
            throw ApiException.notFound("测试用例");
        }
        List<TestPlanCase> rows = planCaseMapper.selectList(Wrappers.<TestPlanCase>lambdaQuery()
                .eq(TestPlanCase::getCaseId, id).orderByDesc(TestPlanCase::getExecutedAt));
        List<Long> planIds = rows.stream().map(TestPlanCase::getPlanId).distinct().toList();
        Map<Long, String> planNames = planIds.isEmpty() ? new HashMap<>()
                : planMapper.selectBatchIds(planIds).stream()
                        .collect(HashMap<Long, String>::new, (m, pl) -> m.put(pl.getId(), pl.getName()), HashMap::putAll);
        List<Long> executorIds = rows.stream().map(TestPlanCase::getExecutorId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = executorIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(executorIds).stream()
                        .collect(HashMap<Long, String>::new, (m, u) -> m.put(u.getId(), u.getNickname()), HashMap::putAll);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TestPlanCase r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("planId", r.getPlanId());
            m.put("planName", planNames.get(r.getPlanId()));
            m.put("result", r.getResult());
            m.put("note", r.getNote());
            m.put("executorId", r.getExecutorId());
            m.put("executorName", r.getExecutorId() == null ? null : nicknames.get(r.getExecutorId()));
            m.put("executedAt", r.getExecutedAt());
            out.add(m);
        }
        return Result.ok(out);
    }

    private Map<String, Object> baseVo(TestPlan p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("projectId", p.getProjectId());
        m.put("name", p.getName());
        m.put("ownerId", p.getOwnerId());
        m.put("stage", p.getStage());
        m.put("iterationId", p.getIterationId());
        m.put("status", p.getStatus());
        return m;
    }

    private Map<String, Object> progress(Long planId) {
        List<TestPlanCase> rows = planCaseMapper.selectList(Wrappers.<TestPlanCase>lambdaQuery()
                .eq(TestPlanCase::getPlanId, planId));
        Map<String, Object> m = new HashMap<>();
        m.put("caseCount", rows.size());
        m.put("executedCount", rows.stream().filter(r -> !"UNEXECUTED".equals(r.getResult())).count());
        m.put("passCount", rows.stream().filter(r -> "PASS".equals(r.getResult())).count());
        m.put("failCount", rows.stream().filter(r -> "FAIL".equals(r.getResult())).count());
        m.put("blockedCount", rows.stream().filter(r -> "BLOCKED".equals(r.getResult())).count());
        return m;
    }

    private List<Map<String, Object>> caseRows(Long planId) {
        List<TestPlanCase> rows = planCaseMapper.selectList(Wrappers.<TestPlanCase>lambdaQuery()
                .eq(TestPlanCase::getPlanId, planId));
        List<Long> caseIds = rows.stream().map(TestPlanCase::getCaseId).toList();
        Map<Long, TestCase> caseMap = caseIds.isEmpty() ? new HashMap<>()
                : caseMapper.selectBatchIds(caseIds).stream()
                        .collect(HashMap<Long, TestCase>::new, (m, c) -> m.put(c.getId(), c), HashMap::putAll);
        List<Long> executorIds = rows.stream().map(TestPlanCase::getExecutorId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = executorIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(executorIds).stream()
                        .collect(HashMap<Long, String>::new, (m, u) -> m.put(u.getId(), u.getNickname()), HashMap::putAll);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TestPlanCase r : rows) {
            TestCase c = caseMap.get(r.getCaseId());
            Map<String, Object> m = new HashMap<>();
            m.put("caseId", r.getCaseId());
            m.put("caseNo", c != null ? c.getCaseNo() : null);
            m.put("title", c != null ? c.getTitle() : null);
            m.put("priority", c != null ? c.getPriority() : null);
            m.put("executorId", r.getExecutorId());
            m.put("executorName", r.getExecutorId() == null ? null : nicknames.get(r.getExecutorId()));
            m.put("result", r.getResult());
            m.put("note", r.getNote());
            m.put("executedAt", r.getExecutedAt());
            out.add(m);
        }
        return out;
    }

    private TestPlan getOrThrow(Long id) {
        TestPlan p = planMapper.selectById(id);
        if (p == null) {
            throw ApiException.notFound("测试计划");
        }
        return p;
    }
}
