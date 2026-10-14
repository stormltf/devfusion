package com.devfusion.host;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.common.ApiException;
import com.devfusion.common.Result;
import com.devfusion.host.mapper.HostGroupMapper;
import com.devfusion.host.mapper.HostMapper;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 主机组与主机（状态模拟） */
@RestController
@RequestMapping("/api/host-groups")
public class HostController {

    private final HostGroupMapper groupMapper;
    private final HostMapper hostMapper;

    public HostController(HostGroupMapper groupMapper, HostMapper hostMapper) {
        this.groupMapper = groupMapper;
        this.hostMapper = hostMapper;
    }

    @Data
    public static class GroupReq {
        private String name;
        private String description;
    }

    @Data
    public static class HostReq {
        private String name;
        private String ip;
        private String status;
    }

    @GetMapping
    public Result<List<HostGroup>> listGroups() {
        return Result.ok(groupMapper.selectList(Wrappers.<HostGroup>lambdaQuery().orderByDesc(HostGroup::getId)));
    }

    @PostMapping
    public Result<HostGroup> createGroup(@RequestBody GroupReq req) {
        HostGroup g = new HostGroup();
        g.setName(req.getName());
        g.setDescription(req.getDescription());
        groupMapper.insert(g);
        return Result.ok(g);
    }

    @GetMapping("/{id}/hosts")
    public Result<List<Host>> hosts(@PathVariable Long id) {
        return Result.ok(hostMapper.selectList(Wrappers.<Host>lambdaQuery().eq(Host::getGroupId, id)));
    }

    @PostMapping("/{id}/hosts")
    public Result<Host> addHost(@PathVariable Long id, @RequestBody HostReq req) {
        if (groupMapper.selectById(id) == null) {
            throw ApiException.notFound("主机组");
        }
        Host h = new Host();
        h.setGroupId(id);
        h.setName(req.getName());
        h.setIp(req.getIp());
        h.setStatus(req.getStatus() == null ? "ONLINE" : req.getStatus());
        hostMapper.insert(h);
        return Result.ok(h);
    }

    @DeleteMapping("/hosts/{hostId}")
    public Result<Void> removeHost(@PathVariable Long hostId) {
        hostMapper.deleteById(hostId);
        return Result.ok();
    }
}
