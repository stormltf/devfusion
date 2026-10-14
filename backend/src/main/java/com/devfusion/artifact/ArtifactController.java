package com.devfusion.artifact;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.artifact.mapper.ArtifactMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private final ArtifactMapper mapper;

    public ArtifactController(ArtifactMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(@RequestParam(required = false) Long applicationId,
                                                @RequestParam(required = false) String stage,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        Page<Artifact> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<Artifact>lambdaQuery()
                        .eq(applicationId != null, Artifact::getApplicationId, applicationId)
                        .eq(stage != null && !stage.isBlank(), Artifact::getStage, stage)
                        .orderByDesc(Artifact::getId));
        List<Map<String, Object>> rows = p.getRecords().stream().map(a -> toVo(a)).toList();
        PageResult<Map<String, Object>> pr = new PageResult<>();
        pr.setRecords(rows);
        pr.setTotal(p.getTotal());
        pr.setPage(p.getCurrent());
        pr.setSize(p.getSize());
        return Result.ok(pr);
    }

    /** 晋级：半成品 -> 成品，生成三位发布版本（按应用内现有 RELEASE 递增，校验唯一） */
    @PostMapping("/{id}/promote")
    public Result<Map<String, Object>> promote(@PathVariable Long id) {
        Artifact a = mapper.selectById(id);
        if (a == null) {
            throw ApiException.notFound("制品");
        }
        if ("RELEASE".equals(a.getStage())) {
            throw new ApiException("该制品已是成品");
        }
        // 找应用内最大三位版本，patch+1
        int major = 1, minor = 0, patch = 0;
        List<Artifact> releases = mapper.selectList(Wrappers.<Artifact>lambdaQuery()
                .eq(Artifact::getApplicationId, a.getApplicationId())
                .eq(Artifact::getStage, "RELEASE"));
        for (Artifact r : releases) {
            String v = r.getReleaseVersion();
            if (v != null && v.matches("\\d+\\.\\d+\\.\\d+")) {
                String[] parts = v.split("\\.");
                int pm = Integer.parseInt(parts[0]);
                int mi = Integer.parseInt(parts[1]);
                int pp = Integer.parseInt(parts[2]);
                if (pm > major || (pm == major && mi > minor)
                        || (pm == major && mi == minor && pp >= patch)) {
                    major = pm;
                    minor = mi;
                    patch = pp + 1;
                }
            }
        }
        String version = major + "." + minor + "." + patch;
        a.setStage("RELEASE");
        a.setReleaseVersion(version);
        mapper.updateById(a);
        return Result.ok(toVo(a));
    }

    private Map<String, Object> toVo(Artifact a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("applicationId", a.getApplicationId());
        m.put("version", a.getVersion());
        m.put("pipelineRunId", a.getPipelineRunId());
        m.put("type", a.getType());
        m.put("stage", a.getStage());
        m.put("releaseVersion", a.getReleaseVersion());
        m.put("path", a.getPath());
        m.put("sizeBytes", a.getSizeBytes());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }
}
