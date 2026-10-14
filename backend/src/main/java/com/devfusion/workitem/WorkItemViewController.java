package com.devfusion.workitem;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.CurrentUser;
import com.devfusion.common.Result;
import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import com.devfusion.workitem.mapper.WorkItemViewMapper;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 已保存视图：私有视图仅创建者可见 */
@RestController
public class WorkItemViewController {

    private final WorkItemViewMapper viewMapper;
    private final UserMapper userMapper;

    public WorkItemViewController(WorkItemViewMapper viewMapper, UserMapper userMapper) {
        this.viewMapper = viewMapper;
        this.userMapper = userMapper;
    }

    @Data
    public static class ViewReq {
        private String type;
        private String name;
        private Integer isPublic;
        private String filterJson;
        private String sort;
    }

    @GetMapping("/api/projects/{projectId}/views")
    public Result<List<Map<String, Object>>> list(@PathVariable Long projectId,
                                                  @RequestParam(required = false) String type) {
        Long me = CurrentUser.id();
        List<WorkItemView> views = viewMapper.selectList(Wrappers.<WorkItemView>lambdaQuery()
                .eq(WorkItemView::getProjectId, projectId)
                .eq(type != null && !type.isBlank(), WorkItemView::getType, type)
                .and(w -> w.eq(WorkItemView::getIsPublic, 1).or().eq(WorkItemView::getOwnerId, me))
                .orderByDesc(WorkItemView::getId));
        return Result.ok(toVos(views));
    }

    @PostMapping("/api/projects/{projectId}/views")
    @Transactional
    public Result<WorkItemView> create(@PathVariable Long projectId, @RequestBody ViewReq req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ApiException("name 不能为空");
        }
        WorkItemView v = new WorkItemView();
        v.setProjectId(projectId);
        v.setType(req.getType());
        v.setName(req.getName());
        v.setIsPublic(req.getIsPublic() != null && req.getIsPublic() == 1 ? 1 : 0);
        v.setOwnerId(CurrentUser.id());
        v.setFilterJson(req.getFilterJson());
        v.setSort(req.getSort());
        viewMapper.insert(v);
        return Result.ok(v);
    }

    @PutMapping("/api/views/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @RequestBody ViewReq req) {
        WorkItemView v = getOwnedOrThrow(id);
        if (req.getName() != null && !req.getName().isBlank()) {
            v.setName(req.getName());
        }
        if (req.getIsPublic() != null) {
            v.setIsPublic(req.getIsPublic() == 1 ? 1 : 0);
        }
        v.setFilterJson(req.getFilterJson());
        v.setSort(req.getSort());
        viewMapper.updateById(v);
        return Result.ok();
    }

    @DeleteMapping("/api/views/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        getOwnedOrThrow(id);
        viewMapper.deleteById(id);
        return Result.ok();
    }

    private WorkItemView getOwnedOrThrow(Long id) {
        WorkItemView v = viewMapper.selectById(id);
        if (v == null) {
            throw ApiException.notFound("视图");
        }
        if (v.getIsPublic() != null && v.getIsPublic() == 0
                && v.getOwnerId() != null && !v.getOwnerId().equals(CurrentUser.id())) {
            throw ApiException.forbidden();
        }
        return v;
    }

    private List<Map<String, Object>> toVos(List<WorkItemView> views) {
        List<Long> ownerIds = views.stream().map(WorkItemView::getOwnerId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = ownerIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(ownerIds).stream()
                        .collect(HashMap<Long, String>::new, (m, u) -> m.put(u.getId(), u.getNickname()), HashMap::putAll);
        return views.stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("projectId", v.getProjectId());
            m.put("type", v.getType());
            m.put("name", v.getName());
            m.put("isPublic", v.getIsPublic());
            m.put("ownerId", v.getOwnerId());
            m.put("ownerName", v.getOwnerId() == null ? null : nicknames.get(v.getOwnerId()));
            m.put("filterJson", v.getFilterJson());
            m.put("sort", v.getSort());
            return m;
        }).toList();
    }
}
