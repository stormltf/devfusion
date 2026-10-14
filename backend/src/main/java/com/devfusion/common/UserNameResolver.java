package com.devfusion.common;

import com.devfusion.user.User;
import com.devfusion.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** 用户名批量解析：避免 N+1，查不到时统一返回「已删除用户」，前端不再拼「用户#id」兜底。 */
@Component
public class UserNameResolver {

    public static final String DELETED = "已删除用户";

    private final UserMapper userMapper;

    public UserNameResolver(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 批量解析 id -> 昵称；未命中（含已逻辑删除）的 id 映射为「已删除用户」。null id 不在结果中。 */
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        HashSet<Long> distinct = new HashSet<>(ids);
        distinct.remove(null);
        if (distinct.isEmpty()) {
            return out;
        }
        Map<Long, String> found = userMapper.selectBatchIds(distinct).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
        for (Long id : distinct) {
            out.put(id, found.getOrDefault(id, DELETED));
        }
        return out;
    }

    /** 单个解析；null 返回 null。 */
    public String name(Long id) {
        if (id == null) {
            return null;
        }
        return names(java.util.List.of(id)).get(id);
    }
}
