package com.devfusion.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.common.UserNameResolver;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workflow.WorkflowService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.devfusion.project.mapper.ProjectMapper;
import com.devfusion.project.mapper.ProjectMemberMapper;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final WorkflowService workflowService;
    private final UserNameResolver userNameResolver;

    @Data
    public static class ProjectReq {
        @NotBlank
        private String key;
        @NotBlank
        private String name;
        private String description;
        private String color;
        private String status;
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;
    }

    @Data
    public static class MemberReq {
        private Long userId;
        private String roleCode;
    }

    /** 列表项：项目 + 成员数 + 工作项数 */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        List<Project> projects = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Project::getName, keyword)
                .orderByDesc(Project::getUpdatedAt));
        List<Map<String, Object>> result = projects.stream().map(p -> {
            Long memberCount = memberMapper.selectCount(
                    Wrappers.<ProjectMember>lambdaQuery().eq(ProjectMember::getProjectId, p.getId()));
            Map<String, Object> m = toMap(p);
            m.put("memberCount", memberCount);
            m.put("ownerName", userNameResolver.name(p.getOwnerId()));
            return m;
        }).toList();
        return Result.ok(result);
    }

    @PostMapping
    @Transactional
    public Result<Project> create(@RequestBody ProjectReq req) {
        Long count = projectMapper.selectCount(Wrappers.<Project>lambdaQuery().eq(Project::getKey, req.getKey()));
        if (count > 0) {
            throw new ApiException("项目标识已存在");
        }
        Project p = new Project();
        p.setKey(req.getKey().toUpperCase());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setColor(req.getColor());
        p.setStatus("ACTIVE");
        p.setOwnerId(CurrentUser.id());
        p.setStartDate(req.getStartDate());
        p.setEndDate(req.getEndDate());
        projectMapper.insert(p);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(p.getId());
        owner.setUserId(CurrentUser.id());
        owner.setRoleCode("OWNER");
        owner.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(owner);
        // 初始化三类工作项的默认工作流状态
        workflowService.initForProject(p.getId());
        return Result.ok(p);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Project p = getOrThrow(id);
        Map<String, Object> m = toMap(p);
        m.put("ownerName", userNameResolver.name(p.getOwnerId()));
        m.put("memberCount", memberMapper.selectCount(
                Wrappers.<ProjectMember>lambdaQuery().eq(ProjectMember::getProjectId, id)));
        return Result.ok(m);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProjectReq req) {
        Project p = getOrThrow(id);
        if (req.getName() != null) {
            p.setName(req.getName());
        }
        if (req.getDescription() != null) {
            p.setDescription(req.getDescription());
        }
        if (req.getColor() != null) {
            p.setColor(req.getColor());
        }
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
        }
        if (req.getStartDate() != null) {
            p.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null) {
            p.setEndDate(req.getEndDate());
        }
        projectMapper.updateById(p);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getOrThrow(id);
        projectMapper.deleteById(id);
        return Result.ok();
    }

    // ---------- 成员 ----------

    @GetMapping("/{id}/members")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        getOrThrow(id);
        List<ProjectMember> members = memberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, id).orderByAsc(ProjectMember::getId));
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).toList();
        Map<Long, String> nicknames = userNameResolver.names(
                members.stream().map(ProjectMember::getUserId).toList());
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = members.stream().map(m -> {
            User u = users.get(m.getUserId());
            Map<String, Object> vo = new java.util.HashMap<>();
            vo.put("id", m.getId());
            vo.put("userId", m.getUserId());
            vo.put("roleCode", m.getRoleCode());
            vo.put("joinedAt", m.getJoinedAt());
            vo.put("nickname", nicknames.get(m.getUserId()));
            vo.put("username", u != null ? u.getUsername() : null);
            return vo;
        }).toList();
        return Result.ok(result);
    }

    @PostMapping("/{id}/members")
    @Transactional
    public Result<Void> addMember(@PathVariable Long id, @RequestBody MemberReq req) {
        getOrThrow(id);
        Long exists = memberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, id).eq(ProjectMember::getUserId, req.getUserId()));
        if (exists > 0) {
            throw new ApiException("该用户已是项目成员");
        }
        ProjectMember m = new ProjectMember();
        m.setProjectId(id);
        m.setUserId(req.getUserId());
        m.setRoleCode(req.getRoleCode() == null ? "MEMBER" : req.getRoleCode());
        m.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(m);
        return Result.ok();
    }

    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateMember(@PathVariable Long id, @PathVariable Long userId, @RequestBody MemberReq req) {
        ProjectMember m = memberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, id).eq(ProjectMember::getUserId, userId));
        if (m == null) {
            throw ApiException.notFound("成员");
        }
        if ("OWNER".equals(m.getRoleCode())) {
            throw new ApiException("项目创建者角色不可修改");
        }
        m.setRoleCode(req.getRoleCode());
        memberMapper.updateById(m);
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        ProjectMember m = memberMapper.selectOne(Wrappers.<ProjectMember>lambdaQuery()
                .eq(ProjectMember::getProjectId, id).eq(ProjectMember::getUserId, userId));
        if (m == null) {
            throw ApiException.notFound("成员");
        }
        if ("OWNER".equals(m.getRoleCode())) {
            throw new ApiException("不能移除项目创建者");
        }
        memberMapper.deleteById(m.getId());
        return Result.ok();
    }

    private Project getOrThrow(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null) {
            throw ApiException.notFound("项目");
        }
        return p;
    }

    private Map<String, Object> toMap(Project p) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", p.getId());
        m.put("key", p.getKey());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("ownerId", p.getOwnerId());
        m.put("status", p.getStatus());
        m.put("color", p.getColor());
        m.put("startDate", p.getStartDate());
        m.put("endDate", p.getEndDate());
        m.put("createdAt", p.getCreatedAt());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }
}
