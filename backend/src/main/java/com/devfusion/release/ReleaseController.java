package com.devfusion.release;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.release.mapper.ReleaseMapper;
import com.devfusion.workitem.WorkItem;
import com.devfusion.workitem.mapper.WorkItemMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 发布 Release：CRUD + 状态流转（PLANNED→RELEASED→ARCHIVED / →CANCELED）+ 关联工作项 */
@RestController
public class ReleaseController {

    private final ReleaseMapper releaseMapper;
    private final WorkItemMapper itemMapper;

    public ReleaseController(ReleaseMapper releaseMapper, WorkItemMapper itemMapper) {
        this.releaseMapper = releaseMapper;
        this.itemMapper = itemMapper;
    }

    @Data
    public static class ReleaseReq {
        private String name;
        private String version;
        private LocalDate releaseDate;
        private String description;
    }

    @Data
    public static class StatusReq {
        private String status;
    }

    @GetMapping("/api/projects/{projectId}/releases")
    public Result<List<Map<String, Object>>> list(@PathVariable Long projectId) {
        List<Release> releases = releaseMapper.selectList(Wrappers.<Release>lambdaQuery()
                .eq(Release::getProjectId, projectId).orderByDesc(Release::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Release r : releases) {
            Map<String, Object> m = baseVo(r);
            long total = itemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                    .eq(WorkItem::getReleaseId, r.getId()));
            m.put("itemCount", total);
            out.add(m);
        }
        return Result.ok(out);
    }

    @PostMapping("/api/projects/{projectId}/releases")
    @Transactional
    public Result<Release> create(@PathVariable Long projectId, @RequestBody ReleaseReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ApiException("name 不能为空");
        }
        if (req.getVersion() == null || req.getVersion().isBlank()) {
            throw new ApiException("version 不能为空");
        }
        Release r = new Release();
        r.setProjectId(projectId);
        r.setName(req.getName());
        r.setVersion(req.getVersion());
        r.setReleaseDate(req.getReleaseDate());
        r.setDescription(req.getDescription());
        r.setStatus("PLANNED");
        releaseMapper.insert(r);
        return Result.ok(r);
    }

    @GetMapping("/api/releases/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Release r = getOrThrow(id);
        Map<String, Object> m = baseVo(r);
        List<WorkItem> items = itemMapper.selectList(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getReleaseId, id).orderByDesc(WorkItem::getUpdatedAt));
        m.put("items", items);
        return Result.ok(m);
    }

    @PutMapping("/api/releases/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody ReleaseReq req) {
        Release r = getOrThrow(id);
        if (req.getName() != null) {
            r.setName(req.getName());
        }
        if (req.getVersion() != null) {
            r.setVersion(req.getVersion());
        }
        r.setReleaseDate(req.getReleaseDate());
        r.setDescription(req.getDescription());
        releaseMapper.updateById(r);
        return Result.ok();
    }

    @PutMapping("/api/releases/{id}/status")
    @Transactional
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        Release r = getOrThrow(id);
        String from = r.getStatus();
        String to = req.getStatus();
        List<List<String>> allowed = List.of(
                List.of("PLANNED", "RELEASED"),
                List.of("RELEASED", "ARCHIVED"),
                List.of("PLANNED", "CANCELED"));
        if (!allowed.contains(List.of(from, to))) {
            throw new ApiException("发布不允许从 " + from + " 流转到 " + to);
        }
        r.setStatus(to);
        releaseMapper.updateById(r);
        return Result.ok();
    }

    @DeleteMapping("/api/releases/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        Release r = getOrThrow(id);
        long linked = itemMapper.selectCount(Wrappers.<WorkItem>lambdaQuery()
                .eq(WorkItem::getReleaseId, id));
        if (linked > 0) {
            throw new ApiException("发布下关联了 " + linked + " 个工作项，请先移出");
        }
        releaseMapper.deleteById(id);
        return Result.ok();
    }

    private Map<String, Object> baseVo(Release r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("projectId", r.getProjectId());
        m.put("name", r.getName());
        m.put("version", r.getVersion());
        m.put("releaseDate", r.getReleaseDate());
        m.put("status", r.getStatus());
        m.put("description", r.getDescription());
        return m;
    }

    private Release getOrThrow(Long id) {
        Release r = releaseMapper.selectById(id);
        if (r == null) {
            throw ApiException.notFound("发布");
        }
        return r;
    }
}
