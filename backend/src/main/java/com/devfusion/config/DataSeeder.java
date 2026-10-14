package com.devfusion.config;

import com.devfusion.app.Application;
import com.devfusion.app.mapper.ApplicationMapper;
import com.devfusion.artifact.Artifact;
import com.devfusion.artifact.mapper.ArtifactMapper;
import com.devfusion.deploy.Deployment;
import com.devfusion.deploy.mapper.DeploymentMapper;
import com.devfusion.environment.Environment;
import com.devfusion.environment.mapper.EnvironmentMapper;
import com.devfusion.iteration.Iteration;
import com.devfusion.iteration.mapper.IterationMapper;
import com.devfusion.pipeline.Pipeline;
import com.devfusion.pipeline.PipelineJob;
import com.devfusion.pipeline.PipelineStage;
import com.devfusion.pipeline.mapper.PipelineJobMapper;
import com.devfusion.pipeline.mapper.PipelineMapper;
import com.devfusion.pipeline.mapper.PipelineStageMapper;
import com.devfusion.project.Project;
import com.devfusion.project.ProjectMember;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.project.mapper.ProjectMemberMapper;
import com.devfusion.release.Release;
import com.devfusion.release.mapper.ReleaseMapper;
import com.devfusion.testcase.TestCase;
import com.devfusion.testcase.mapper.TestCaseMapper;
import com.devfusion.testplan.TestPlan;
import com.devfusion.testplan.TestPlanCase;
import com.devfusion.testplan.mapper.TestPlanCaseMapper;
import com.devfusion.testplan.mapper.TestPlanMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workflow.WorkflowService;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.WorkItemActivity;
import com.devfusion.workitem.WorkItemView;
import com.devfusion.workitem.Worklog;
import com.devfusion.workitem.mapper.WorkItemActivityMapper;
import com.devfusion.workitem.mapper.WorkItemMapper;
import com.devfusion.workitem.mapper.WorkItemViewMapper;
import com.devfusion.workitem.mapper.WorklogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 首次启动时写入演示数据（user 表为空才执行，可重复启动） */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final IterationMapper iterationMapper;
    private final WorkItemMapper itemMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApplicationMapper applicationMapper;
    private final EnvironmentMapper environmentMapper;
    private final PipelineMapper pipelineMapper;
    private final PipelineStageMapper stageMapper;
    private final PipelineJobMapper jobMapper;
    private final ArtifactMapper artifactMapper;
    private final DeploymentMapper deploymentMapper;
    private final WorkflowService workflowService;
    private final ReleaseMapper releaseMapper;
    private final TestPlanMapper testPlanMapper;
    private final TestPlanCaseMapper testPlanCaseMapper;
    private final WorklogMapper worklogMapper;
    private final WorkItemViewMapper workItemViewMapper;
    private final WorkItemActivityMapper activityMapper;
    private final com.devfusion.pipeline.mapper.PipelineVariableMapper opsVariableMapper;
    private final com.devfusion.deploy.mapper.DeployTemplateMapper opsTemplateMapper;
    private final com.devfusion.host.mapper.HostGroupMapper opsGroupMapper;
    private final com.devfusion.host.mapper.HostMapper opsHostMapper;
    private final com.devfusion.notification.mapper.NotificationMapper opsNotificationMapper;
    private final com.devfusion.token.mapper.AccessTokenMapper opsTokenMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("初始化演示数据...");
        Long admin = seedUsers();
        Long projectId = seedProject(admin);
        seedWorkItems(projectId, admin);
        seedTestCases(projectId, admin);
        Map<String, Long> envIds = seedEnvironments();
        seedDevOps(admin, envIds);
        seedPmExtras(projectId, admin);
        seedOpsExtras(admin, envIds);
        log.info("演示数据初始化完成：admin / admin123 可登录");
    }

    private Long seedUsers() {
        List<User> users = List.of(
                buildUser("admin", "管理员", "ADMIN"),
                buildUser("zhangwei", "张伟", "MEMBER"),
                buildUser("lina", "李娜", "MEMBER"),
                buildUser("wangqiang", "王强", "MEMBER"));
        users.forEach(userMapper::insert);
        return users.get(0).getId();
    }

    private User buildUser(String username, String nickname, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode("admin123"));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus(1);
        u.setEmail(username + "@devfusion.local");
        return u;
    }

    private Long seedProject(Long adminId) {
        Project p = new Project();
        p.setKey("GWP");
        p.setName("智能网关平台");
        p.setDescription("API 网关产品研发，含控制台与网关内核两条线");
        p.setOwnerId(adminId);
        p.setStatus("ACTIVE");
        p.setColor("#2563eb");
        p.setStartDate(LocalDate.now().minusDays(30));
        p.setEndDate(LocalDate.now().plusDays(60));
        projectMapper.insert(p);

        for (Long uid : userMapper.selectList(null).stream().map(User::getId).toList()) {
            ProjectMember m = new ProjectMember();
            m.setProjectId(p.getId());
            m.setUserId(uid);
            m.setRoleCode(uid.equals(adminId) ? "OWNER" : "MEMBER");
            m.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m);
        }
        workflowService.initForProject(p.getId());

        Iteration it1 = new Iteration();
        it1.setProjectId(p.getId());
        it1.setName("迭代一：路由引擎");
        it1.setGoal("完成动态路由与限流核心能力");
        it1.setStatus("IN_PROGRESS");
        it1.setStartDate(LocalDate.now().minusDays(10));
        it1.setEndDate(LocalDate.now().plusDays(4));
        it1.setCreatedBy(adminId);
        iterationMapper.insert(it1);

        Iteration it2 = new Iteration();
        it2.setProjectId(p.getId());
        it2.setName("迭代二：灰度发布");
        it2.setGoal("按流量比例灰度与监控大盘");
        it2.setStatus("PLANNED");
        it2.setStartDate(LocalDate.now().plusDays(5));
        it2.setEndDate(LocalDate.now().plusDays(19));
        it2.setCreatedBy(adminId);
        iterationMapper.insert(it2);
        return p.getId();
    }

    private Long addItem(Long projectId, String type, String title, String status, String priority,
                         Long assignee, Long iteration, Long parent, String desc) {
        WorkItem item = new WorkItem();
        item.setProjectId(projectId);
        item.setType(type);
        item.setTitle(title);
        item.setStatusCode(status);
        item.setPriority(priority);
        item.setAssigneeId(assignee);
        item.setIterationId(iteration);
        item.setParentId(parent);
        item.setDescription(desc);
        item.setReporterId(assignee);
        // 编号与业务路径共用 Redis 计数器，避免种子数据与后续创建的编号冲突
        Long seq = redis.opsForValue().increment("df:seq:" + projectId + ":" + type);
        item.setItemNo("GWP-" + type + "-" + seq);
        itemMapper.insert(item);
        return item.getId();
    }

    private void seedWorkItems(Long projectId, Long adminId) {
        Long zhang = userMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery().eq(User::getUsername, "zhangwei")).getId();
        Long li = userMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery().eq(User::getUsername, "lina")).getId();

        Long iter1 = iterationMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Iteration>lambdaQuery()
                        .eq(Iteration::getProjectId, projectId)).get(0).getId();

        Long s1 = addItem(projectId, "STORY", "支持基于权重的动态路由", "IN_PROGRESS", "HIGH", zhang, iter1, null,
                "网关根据服务实例权重转发流量，权重可在控制台热更新。");
        Long s2 = addItem(projectId, "STORY", "接口级限流与熔断", "TODO", "URGENT", li, iter1, null,
                "对指定路由配置 QPS 阈值，超出后快速失败并上报指标。");
        addItem(projectId, "STORY", "灰度发布流量标记", "TODO", "MEDIUM", null, null, null,
                "规划中：按用户标签打流量标记，用于灰度观察。");

        addItem(projectId, "TASK", "设计权重路由数据模型", "DONE", "HIGH", zhang, iter1, s1, "表结构与缓存键设计");
        addItem(projectId, "TASK", "实现权重选择器", "IN_PROGRESS", "HIGH", zhang, iter1, s1, "平滑加权轮询实现");
        addItem(projectId, "TASK", "控制台权重配置页", "TODO", "MEDIUM", li, iter1, s1, "前端配置界面");
        addItem(projectId, "TASK", "限流规则存储与下发", "IN_PROGRESS", "URGENT", li, iter1, s2, "规则变更广播到网关节点");

        addItem(projectId, "BUG", "权重为 0 的节点仍收到流量", "NEW", "URGENT", zhang, iter1, s1, "边界条件：weight=0 应被剔除");
        addItem(projectId, "BUG", "限流阈值热更新偶发不生效", "FIXING", "HIGH", li, iter1, s2, "怀疑是监听器重复注册");
        addItem(projectId, "BUG", "控制台路由列表分页错乱", "CLOSED", "LOW", li, iter1, null, "已修复并验证");
    }

    private void seedTestCases(Long projectId, Long adminId) {
        TestCase t1 = buildCase(projectId, "权重路由-正常转发", "网关启动且节点权重已配置",
                "配置 A/B 节点权重 70/30，连续发起 100 次请求", "A≈70 次，B≈30 次", "HIGH", "路由");
        t1.setLatestResult("PASS");
        t1.setLatestExecutorId(adminId);
        t1.setLatestExecutedAt(LocalDateTime.now().minusDays(1));
        testCaseMapper.insert(t1);

        TestCase t2 = buildCase(projectId, "权重路由-零权重节点剔除", "存在 weight=0 的节点",
                "将节点 B 权重设为 0 后发起请求", "B 节点不收到任何流量", "URGENT", "路由");
        t2.setLatestResult("FAIL");
        t2.setLatestExecutorId(adminId);
        t2.setLatestExecutedAt(LocalDateTime.now().minusHours(3));
        testCaseMapper.insert(t2);

        TestCase t3 = buildCase(projectId, "限流-超过阈值快速失败", "路由已配置 QPS=100",
                "以 200 QPS 压测 30 秒", "超出部分返回 429，网关无雪崩", "HIGH", "限流");
        testCaseMapper.insert(t3);
    }

    private TestCase buildCase(Long projectId, String title, String precondition,
                               String steps, String expected, String priority, String module) {
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        Long seq = redis.opsForValue().increment("df:seq:" + projectId + ":TESTCASE");
        tc.setCaseNo("GWP-TC-" + seq);
        tc.setTitle(title);
        tc.setPrecondition(precondition);
        tc.setSteps("[{\"action\":\"" + steps + "\",\"expected\":\"" + expected + "\"}]");
        tc.setExpected(expected);
        tc.setPriority(priority);
        tc.setModule(module);
        return tc;
    }

    private Map<String, Long> seedEnvironments() {
        Long dev = env("DEV", "开发环境", "DEV", 1);
        Long test = env("TEST", "测试环境", "TEST", 2);
        Long staging = env("STAGING", "预发环境", "STAGING", 3);
        Long prod = env("PROD", "生产环境", "PROD", 4);
        return Map.of("DEV", dev, "TEST", test, "STAGING", staging, "PROD", prod);
    }

    private Long env(String key, String name, String type, int sort) {
        Environment e = new Environment();
        e.setKey(key);
        e.setName(name);
        e.setType(type);
        e.setSort(sort);
        e.setHosts("10.0." + sort + ".11, 10.0." + sort + ".12");
        environmentMapper.insert(e);
        return e.getId();
    }

    private void seedDevOps(Long adminId, Map<String, Long> envIds) {
        Application gateway = new Application();
        gateway.setKey("GATEWAY");
        gateway.setName("gateway-service");
        gateway.setDescription("网关核心服务（Java）");
        gateway.setRepoUrl("git@git.devfusion.local:demo/gateway-service.git");
        gateway.setLanguage("JAVA");
        gateway.setOwnerId(adminId);
        applicationMapper.insert(gateway);

        Application console = new Application();
        console.setKey("CONSOLE");
        console.setName("gateway-console");
        console.setDescription("网关控制台（前端）");
        console.setRepoUrl("git@git.devfusion.local:demo/gateway-console.git");
        console.setLanguage("NODEJS");
        console.setOwnerId(adminId);
        applicationMapper.insert(console);

        Long p1 = createPipeline(gateway.getId(), "gateway 构建流水线", "编译 + 单测 + 镜像 + 制品", "WEBHOOK", adminId);
        Long s1 = stage(p1, "构建与测试", 1);
        Long s2 = stage(p1, "镜像与制品", 2);
        addJob(s1, "编译打包", "MAVEN_BUILD", "{\"goals\":\"clean package\"}", 1);
        addJob(s1, "单元测试", "UNIT_TEST", "{\"testCases\":128}", 2);
        addJob(s1, "冒烟自检", "SHELL", "{\"script\":\"echo smoke-check ok\"}", 3);
        addJob(s2, "构建镜像", "DOCKER_BUILD", "{\"tag\":\"registry.devfusion.local/gateway\"}", 1);
        addJob(s2, "归档制品", "ARTIFACT_UPLOAD", "{\"artifactType\":\"IMAGE\"}", 2);

        Long p2 = createPipeline(console.getId(), "console 构建流水线", "前端构建 + 制品", "MANUAL", adminId);
        Long s3 = stage(p2, "构建", 1);
        Long s4 = stage(p2, "发布", 2);
        addJob(s3, "npm 构建", "NPM_BUILD", "{}", 1);
        addJob(s3, "回显环境信息", "SHELL", "{\"script\":\"echo build for staging\"}", 2);
        addJob(s4, "归档制品", "ARTIFACT_UPLOAD", "{\"artifactType\":\"NPM\"}", 1);

        // 历史制品与部署（让总览页有数据；构建历史由 e2e 触发产生）
        Artifact a1 = artifact(gateway.getId(), "GATEWAY-20261009-1", "IMAGE");
        Artifact a2 = artifact(gateway.getId(), "GATEWAY-20261011-2", "IMAGE");
        deploy(gateway.getId(), a1, envIds.get("DEV"), "20261009");
        deploy(gateway.getId(), a2, envIds.get("TEST"), "20261011");
        Artifact a3 = artifact(console.getId(), "CONSOLE-20261010-3", "NPM");
        deploy(console.getId(), a3, envIds.get("TEST"), "20261010");
    }

    private Long createPipeline(Long appId, String name, String desc, String trigger, Long userId) {
        Pipeline p = new Pipeline();
        p.setApplicationId(appId);
        p.setName(name);
        p.setDescription(desc);
        p.setTriggerType(trigger);
        p.setStatus("ENABLED");
        p.setWebhookToken(java.util.UUID.randomUUID().toString().replace("-", ""));
        p.setCreatedBy(userId);
        pipelineMapper.insert(p);
        return p.getId();
    }

    private Long stage(Long pipelineId, String name, int sort) {
        PipelineStage s = new PipelineStage();
        s.setPipelineId(pipelineId);
        s.setName(name);
        s.setSort(sort);
        stageMapper.insert(s);
        return s.getId();
    }

    private void addJob(Long stageId, String name, String type, String config, int sort) {
        PipelineJob j = new PipelineJob();
        j.setStageId(stageId);
        j.setName(name);
        j.setType(type);
        j.setConfig(config);
        j.setSort(sort);
        jobMapper.insert(j);
    }

    private Artifact artifact(Long appId, String version, String type) {
        Artifact a = new Artifact();
        a.setApplicationId(appId);
        a.setVersion(version);
        a.setType(type);
        a.setPath("registry.devfusion.local/" + version.toLowerCase());
        a.setSizeBytes(45_000_000L);
        artifactMapper.insert(a);
        return a;
    }

    private void deploy(Long appId, Artifact a, Long envId, String day) {
        Deployment d = new Deployment();
        d.setApplicationId(appId);
        d.setArtifactId(a.getId());
        d.setEnvironmentId(envId);
        d.setVersion(a.getVersion());
        d.setDeployType("DEPLOY");
        d.setStatus("SUCCESS");
        d.setOperatorId(1L);
        d.setLog("[done] 部署成功，当前运行版本 " + a.getVersion());
        d.setStartedAt(LocalDateTime.now().withDayOfMonth(Integer.parseInt(day.substring(6, 8))).minusHours(1));
        d.setFinishedAt(d.getStartedAt().plusMinutes(4));
        deploymentMapper.insert(d);
    }

    /** PM 扩展演示数据：发布、测试计划+执行记录、工时、公共视图、燃尽历史、缺陷趋势 */
    private void seedPmExtras(Long projectId, Long adminId) {
        Long zhang = userMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaQuery().eq(User::getUsername, "zhangwei")).getId();

        Release rel = new Release();
        rel.setProjectId(projectId);
        rel.setName("v1.0 首个正式版");
        rel.setVersion("1.0.0");
        rel.setReleaseDate(LocalDate.now().plusDays(3));
        rel.setStatus("RELEASED");
        rel.setDescription("动态路由与限流首发");
        releaseMapper.insert(rel);

        TestPlan plan = new TestPlan();
        plan.setProjectId(projectId);
        plan.setName("路由与限流首轮测试");
        plan.setOwnerId(adminId);
        plan.setStage("SIT");
        plan.setStatus("IN_PROGRESS");
        plan.setCreatedBy(adminId);
        testPlanMapper.insert(plan);

        List<TestCase> cases = testCaseMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TestCase>lambdaQuery().eq(TestCase::getProjectId, projectId));
        LocalDateTime base = LocalDateTime.now();
        String[][] results = {{"PASS", "已回归通过"}, {"FAIL", "零权重节点仍漏流量"}, {null, null}};
        for (int i = 0; i < cases.size() && i < results.length; i++) {
            TestPlanCase pc = new TestPlanCase();
            pc.setPlanId(plan.getId());
            pc.setCaseId(cases.get(i).getId());
            pc.setExecutorId(zhang);
            if (results[i][0] != null) {
                pc.setResult(results[i][0]);
                pc.setNote(results[i][1]);
                pc.setExecutedAt(base.minusDays(1).minusHours(i));
            }
            testPlanCaseMapper.insert(pc);
        }

        WorkItem s1 = itemMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<WorkItem>lambdaQuery()
                        .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getTitle, "支持基于权重的动态路由"));
        if (s1 != null) {
            Worklog w = new Worklog();
            w.setItemId(s1.getId());
            w.setUserId(zhang);
            w.setHours(3.5);
            w.setRemaining(5.0);
            w.setLogDate(LocalDate.now().minusDays(1));
            w.setNote("权重选择器开发");
            worklogMapper.insert(w);
            s1.setEstimateHours(8.0);
            s1.setSpentHours(3.5);
            s1.setRemainingHours(5.0);
            itemMapper.updateById(s1);
        }

        WorkItemView view = new WorkItemView();
        view.setProjectId(projectId);
        view.setType("STORY");
        view.setName("全部需求");
        view.setIsPublic(1);
        view.setOwnerId(adminId);
        view.setFilterJson("{}");
        workItemViewMapper.insert(view);

        // 燃尽历史：把已完成任务/关闭缺陷的状态流转补到过去
        WorkItem doneTask = itemMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<WorkItem>lambdaQuery()
                        .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getTitle, "设计权重路由数据模型"));
        if (doneTask != null) {
            addActivity(doneTask, "STATUS", "{\"from\":\"TODO\",\"to\":\"DONE\"}", LocalDateTime.now().minusDays(6), zhang);
        }
        WorkItem closedBug = itemMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<WorkItem>lambdaQuery()
                        .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getTitle, "控制台路由列表分页错乱"));
        if (closedBug != null) {
            addActivity(closedBug, "STATUS", "{\"from\":\"FIXING\",\"to\":\"CLOSED\"}", LocalDateTime.now().minusDays(4), zhang);
        }

        // 缺陷趋势：把几个缺陷创建时间回写到过去
        backdate(projectId, "权重为 0 的节点仍收到流量", LocalDateTime.now().minusDays(9));
        backdate(projectId, "限流阈值热更新偶发不生效", LocalDateTime.now().minusDays(5));
        backdate(projectId, "控制台路由列表分页错乱", LocalDateTime.now().minusDays(12));
    }

    private void addActivity(WorkItem item, String action, String detail, LocalDateTime at, Long userId) {
        WorkItemActivity a = new WorkItemActivity();
        a.setItemId(item.getId());
        a.setProjectId(item.getProjectId());
        a.setUserId(userId);
        a.setAction(action);
        a.setDetail(detail);
        a.setCreatedAt(at);
        activityMapper.insert(a);
    }

    private void backdate(Long projectId, String title, LocalDateTime at) {
        WorkItem w = itemMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<WorkItem>lambdaQuery()
                        .eq(WorkItem::getProjectId, projectId).eq(WorkItem::getTitle, title));
        if (w != null) {
            w.setCreatedAt(at);
            itemMapper.updateById(w);
        }
    }

    /** DevOps 扩展演示数据：变量、审批流水线、主机组、模板、成品制品、通知、令牌 */
    private void seedOpsExtras(Long adminId, Map<String, Long> envIds) {
        com.devfusion.app.Application gateway = applicationMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.devfusion.app.Application>lambdaQuery()
                        .eq(com.devfusion.app.Application::getKey, "GATEWAY"));
        if (gateway == null) {
            return;
        }
        Long gwAppId = gateway.getId();

        // 流水线参数变量
        com.devfusion.pipeline.Pipeline p1 = pipelineMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.devfusion.pipeline.Pipeline>lambdaQuery()
                        .eq(com.devfusion.pipeline.Pipeline::getApplicationId, gwAppId)
                        .orderByAsc(com.devfusion.pipeline.Pipeline::getId).last("LIMIT 1"));
        if (p1 != null) {
            com.devfusion.pipeline.PipelineVariable v1 = new com.devfusion.pipeline.PipelineVariable();
            v1.setPipelineId(p1.getId());
            v1.setName("DEPLOY_ENV");
            v1.setDefaultValue("staging");
            v1.setSecret(0);
            v1.setRuntimeEditable(1);
            opsVariableMapper.insert(v1);
            com.devfusion.pipeline.PipelineVariable v2 = new com.devfusion.pipeline.PipelineVariable();
            v2.setPipelineId(p1.getId());
            v2.setName("API_TOKEN");
            v2.setDefaultValue("dev-secret-token");
            v2.setSecret(1);
            v2.setRuntimeEditable(1);
            opsVariableMapper.insert(v2);

            // 一条带人工审批 Job 的示例流水线
            Long ap = createPipeline(gwAppId, "gateway 发布审批流水线", "构建后人工审批再部署", "MANUAL", adminId);
            Long as1 = stage(ap, "构建", 1);
            addJob(as1, "编译打包", "MAVEN_BUILD", "{\"goals\":\"clean package\"}", 1);
            Long as2 = stage(ap, "审批", 2);
            addJob(as2, "发布审批", "APPROVAL", "{}", 1);
            Long as3 = stage(ap, "部署", 3);
            addJob(as3, "归档制品", "ARTIFACT_UPLOAD", "{\"artifactType\":\"IMAGE\"}", 1);
        }

        // PROD 环境默认需要审批
        if (envIds.get("PROD") != null) {
            com.devfusion.environment.Environment prod = environmentMapper.selectById(envIds.get("PROD"));
            if (prod != null) {
                prod.setRequireApproval(1);
                environmentMapper.updateById(prod);
            }
        }

        // 主机组 + 主机
        com.devfusion.host.HostGroup group = new com.devfusion.host.HostGroup();
        group.setName("生产网关主机组");
        group.setDescription("gateway-service 生产节点");
        opsGroupMapper.insert(group);
        for (int i = 1; i <= 2; i++) {
            com.devfusion.host.Host h = new com.devfusion.host.Host();
            h.setGroupId(group.getId());
            h.setName("gw-node-" + i);
            h.setIp("10.0.4." + (10 + i));
            h.setStatus("ONLINE");
            opsHostMapper.insert(h);
        }
        if (envIds.get("PROD") != null) {
            com.devfusion.environment.Environment prod = environmentMapper.selectById(envIds.get("PROD"));
            if (prod != null) {
                prod.setHostGroupId(group.getId());
                environmentMapper.updateById(prod);
            }
        }

        // 部署模板
        com.devfusion.deploy.DeployTemplate tpl = new com.devfusion.deploy.DeployTemplate();
        tpl.setAppId(gwAppId);
        tpl.setName("标准主机部署模板");
        tpl.setPreScript("systemctl stop gateway; backup /opt/gateway");
        tpl.setPostScript("systemctl start gateway; sleep 3");
        tpl.setHealthCheckUrl("http://10.0.4.11:8080/actuator/health");
        opsTemplateMapper.insert(tpl);

        // 1 个成品制品（晋级）
        com.devfusion.artifact.Artifact snap = artifactMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.devfusion.artifact.Artifact>lambdaQuery()
                        .eq(com.devfusion.artifact.Artifact::getApplicationId, gwAppId)
                        .orderByDesc(com.devfusion.artifact.Artifact::getId).last("LIMIT 1"));
        if (snap != null) {
            snap.setStage("RELEASE");
            snap.setReleaseVersion("1.0.0");
            artifactMapper.updateById(snap);
        }

        // 若干站内通知
        for (String[] n : new String[][]{
                {"BUILD_FAILED", "构建失败", "网关构建流水线 #8 执行失败", "/runs/8"},
                {"APPROVAL_PENDING", "待审批", "部署单 #3 到生产环境等待审批", "/deployments"}}) {
            com.devfusion.notification.Notification nt = new com.devfusion.notification.Notification();
            nt.setUserId(adminId);
            nt.setType(n[0]);
            nt.setTitle(n[1]);
            nt.setContent(n[2]);
            nt.setLink(n[3]);
            opsNotificationMapper.insert(nt);
        }

        // 1 个令牌（不保存明文）
        com.devfusion.token.AccessToken token = new com.devfusion.token.AccessToken();
        token.setUserId(adminId);
        token.setName("CI 自动触发");
        token.setTokenHash(com.devfusion.token.TokenService.hash("dfpat-seed-demo-token"));
        opsTokenMapper.insert(token);
    }
}
