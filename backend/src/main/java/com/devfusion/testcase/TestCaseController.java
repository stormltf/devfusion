package com.devfusion.testcase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.common.UserNameResolver;
import com.devfusion.project.Project;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.testcase.mapper.TestCaseMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TestCaseController {

    private final TestCaseMapper mapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final UserNameResolver userNameResolver;

    public TestCaseController(TestCaseMapper mapper, ProjectMapper projectMapper,
                              UserMapper userMapper, StringRedisTemplate redis,
                              UserNameResolver userNameResolver) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.redis = redis;
        this.userNameResolver = userNameResolver;
    }

    @Data
    public static class CaseReq {
        @NotBlank
        private String title;
        private String precondition;
        private String steps;
        private String expected;
        private String priority;
        private String module;
    }

    @Data
    public static class ExecuteReq {
        /** PASS / FAIL / BLOCKED */
        @NotBlank
        private String result;
        /** result=FAIL 时可选关联缺陷工作项 */
        private Long bugItemId;
    }

    @GetMapping("/api/projects/{projectId}/test-cases")
    public Result<PageResult<Map<String, Object>>> list(@PathVariable Long projectId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String module,
                                                @RequestParam(required = false) String priority,
                                                @RequestParam(required = false) String result,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        Page<TestCase> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<TestCase>lambdaQuery()
                        .eq(TestCase::getProjectId, projectId)
                        .eq(module != null && !module.isBlank(), TestCase::getModule, module)
                        .eq(priority != null && !priority.isBlank(), TestCase::getPriority, priority)
                        .eq(result != null && !result.isBlank(), TestCase::getLatestResult, result)
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(TestCase::getTitle, keyword)
                                .or().like(TestCase::getCaseNo, keyword))
                        .orderByDesc(TestCase::getId));
        List<TestCase> records = p.getRecords();
        List<Long> executorIds = records.stream().map(TestCase::getLatestExecutorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = userNameResolver.names(executorIds);
        List<Map<String, Object>> rows = records.stream().map(tc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", tc.getId());
            m.put("caseNo", tc.getCaseNo());
            m.put("title", tc.getTitle());
            m.put("precondition", tc.getPrecondition());
            m.put("steps", tc.getSteps());
            m.put("expected", tc.getExpected());
            m.put("priority", tc.getPriority());
            m.put("module", tc.getModule());
            m.put("latestResult", tc.getLatestResult());
            m.put("latestExecutorId", tc.getLatestExecutorId());
            m.put("latestExecutorName", tc.getLatestExecutorId() != null
                    ? nicknames.getOrDefault(tc.getLatestExecutorId(), null) : null);
            m.put("latestExecutedAt", tc.getLatestExecutedAt());
            m.put("bugItemId", tc.getBugItemId());
            m.put("createdAt", tc.getCreatedAt());
            return m;
        }).toList();
        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setRecords(rows);
        pr.setTotal(p.getTotal());
        pr.setPage(p.getCurrent());
        pr.setSize(p.getSize());
        return Result.ok(pr);
    }

    @PostMapping("/api/projects/{projectId}/test-cases")
    @Transactional
    public Result<TestCase> create(@PathVariable Long projectId, @RequestBody CaseReq req) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ApiException.notFound("项目");
        }
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setTitle(req.getTitle());
        tc.setPrecondition(req.getPrecondition());
        tc.setSteps(req.getSteps());
        tc.setExpected(req.getExpected());
        tc.setPriority(req.getPriority() == null ? "MEDIUM" : req.getPriority());
        tc.setModule(req.getModule());
        tc.setLatestResult("UNEXECUTED");
        tc.setCreatorId(CurrentUser.id());
        Long seq = redis.opsForValue().increment("df:seq:" + projectId + ":TESTCASE");
        tc.setCaseNo(project.getKey() + "-TC-" + seq);
        mapper.insert(tc);
        return Result.ok(tc);
    }

    @PutMapping("/api/test-cases/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CaseReq req) {
        TestCase tc = getOrThrow(id);
        tc.setTitle(req.getTitle());
        tc.setPrecondition(req.getPrecondition());
        tc.setSteps(req.getSteps());
        tc.setExpected(req.getExpected());
        if (req.getPriority() != null) {
            tc.setPriority(req.getPriority());
        }
        tc.setModule(req.getModule());
        mapper.updateById(tc);
        return Result.ok();
    }

    @DeleteMapping("/api/test-cases/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        mapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping("/api/test-cases/{id}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long id, @RequestBody ExecuteReq req) {
        TestCase tc = getOrThrow(id);
        if (!List.of("PASS", "FAIL", "BLOCKED").contains(req.getResult())) {
            throw new ApiException("result 必须是 PASS/FAIL/BLOCKED");
        }
        tc.setLatestResult(req.getResult());
        tc.setLatestExecutorId(CurrentUser.id());
        tc.setLatestExecutedAt(LocalDateTime.now());
        if ("FAIL".equals(req.getResult())) {
            tc.setBugItemId(req.getBugItemId());
        }
        mapper.updateById(tc);
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", tc.getId());
        vo.put("latestResult", tc.getLatestResult());
        vo.put("latestExecutorId", tc.getLatestExecutorId());
        vo.put("latestExecutorName", userNameResolver.name(tc.getLatestExecutorId()));
        vo.put("latestExecutedAt", tc.getLatestExecutedAt());
        vo.put("bugItemId", tc.getBugItemId());
        return Result.ok(vo);
    }

    /** 用例执行结果统计 */
    @GetMapping("/api/projects/{projectId}/test-stats")
    public Result<Map<String, Long>> testStats(@PathVariable Long projectId) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", mapper.selectCount(Wrappers.<TestCase>lambdaQuery().eq(TestCase::getProjectId, projectId)));
        for (String r : List.of("UNEXECUTED", "PASS", "FAIL", "BLOCKED")) {
            stats.put(r, mapper.selectCount(Wrappers.<TestCase>lambdaQuery()
                    .eq(TestCase::getProjectId, projectId).eq(TestCase::getLatestResult, r)));
        }
        return Result.ok(stats);
    }

    private TestCase getOrThrow(Long id) {
        TestCase tc = mapper.selectById(id);
        if (tc == null) {
            throw ApiException.notFound("测试用例");
        }
        return tc;
    }
}
