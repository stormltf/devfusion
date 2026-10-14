package com.devfusion.workbench;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.deploy.Deployment;
import com.devfusion.deploy.mapper.DeploymentMapper;
import com.devfusion.runner.PipelineRun;
import com.devfusion.runner.RunJob;
import com.devfusion.runner.mapper.PipelineRunMapper;
import com.devfusion.runner.mapper.RunJobMapper;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.mapper.WorkItemMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 我的工作台：聚合待办工作项 / 我的构建 / 待我审批 / 我的部署 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkItemMapper itemMapper;
    private final PipelineRunMapper runMapper;
    private final RunJobMapper runJobMapper;
    private final DeploymentMapper deploymentMapper;
    private final UserMapper userMapper;

    public WorkbenchController(WorkItemMapper itemMapper, PipelineRunMapper runMapper,
                               RunJobMapper runJobMapper, DeploymentMapper deploymentMapper,
                               UserMapper userMapper) {
        this.itemMapper = itemMapper;
        this.runMapper = runMapper;
        this.runJobMapper = runJobMapper;
        this.deploymentMapper = deploymentMapper;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Long me = CurrentUser.id();
        Map<String, Object> m = new HashMap<>();

        // 指派给我的未完成工作项
        List<WorkItem> mine = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getAssigneeId, me)
                .orderByDesc(WorkItem::getId).last("LIMIT 20"));
        List<Map<String, Object>> myItems = new ArrayList<>();
        for (WorkItem it : mine) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", it.getId());
            r.put("itemNo", it.getItemNo());
            r.put("title", it.getTitle());
            r.put("statusCode", it.getStatusCode());
            r.put("type", it.getType());
            myItems.add(r);
        }
        m.put("myItems", myItems);

        // 我发起的最近构建
        List<PipelineRun> myRuns = runMapper.selectList(Wrappers.<PipelineRun>lambdaQuery()
                .eq(PipelineRun::getTriggerUserId, me)
                .orderByDesc(PipelineRun::getId).last("LIMIT 10"));
        m.put("myRuns", myRuns.stream().map(r -> {
            Map<String, Object> x = new HashMap<>();
            x.put("id", r.getId());
            x.put("pipelineId", r.getPipelineId());
            x.put("version", r.getVersion());
            x.put("status", r.getStatus());
            x.put("createdAt", r.getCreatedAt());
            return x;
        }).toList());

        // 待我审批：审批人规则 = 系统管理员（项目 OWNER/ADMIN 同类，此处取系统管理员）
        List<Map<String, Object>> approvals = new ArrayList<>();
        User u = userMapper.selectById(me);
        if (u != null && "ADMIN".equals(u.getRole())) {
            for (RunJob j : runJobMapper.selectList(Wrappers.<RunJob>lambdaQuery()
                    .eq(RunJob::getStatus, "WAITING_APPROVAL"))) {
                Map<String, Object> x = new HashMap<>();
                x.put("type", "APPROVAL_JOB");
                x.put("runId", j.getRunId());
                x.put("jobId", j.getId());
                x.put("jobName", j.getName());
                approvals.add(x);
            }
            for (Deployment d : deploymentMapper.selectList(Wrappers.<Deployment>lambdaQuery()
                    .eq(Deployment::getStatus, "PENDING_APPROVAL"))) {
                Map<String, Object> x = new HashMap<>();
                x.put("type", "DEPLOYMENT");
                x.put("deploymentId", d.getId());
                x.put("version", d.getVersion());
                approvals.add(x);
            }
        }
        m.put("pendingApprovals", approvals);

        // 我最近的部署
        List<Deployment> myDeps = deploymentMapper.selectList(Wrappers.<Deployment>lambdaQuery()
                .eq(Deployment::getOperatorId, me)
                .orderByDesc(Deployment::getId).last("LIMIT 10"));
        m.put("myDeployments", myDeps.stream().map(d -> {
            Map<String, Object> x = new HashMap<>();
            x.put("id", d.getId());
            x.put("version", d.getVersion());
            x.put("status", d.getStatus());
            x.put("deployType", d.getDeployType());
            x.put("createdAt", d.getCreatedAt());
            return x;
        }).toList());

        return Result.ok(m);
    }
}
