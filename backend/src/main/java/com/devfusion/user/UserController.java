package com.devfusion.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devfusion.common.ApiException;
import com.devfusion.common.PageResult;
import com.devfusion.common.Result;
import com.devfusion.user.mapper.UserMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Data
    public static class UserUpsertReq {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        @NotBlank
        private String nickname;
        private String email;
        private String phone;
        private String role = "MEMBER";
    }

    @Data
    public static class UserUpdateReq {
        private String nickname;
        private String email;
        private String phone;
        private String role;
        private Integer status;
    }

    @Data
    public static class PasswordReq {
        @NotBlank
        private String password;
    }

    private void requireAdmin() {
        User me = userMapper.selectById(com.devfusion.common.CurrentUser.id());
        if (me == null || !"ADMIN".equals(me.getRole())) {
            throw ApiException.forbidden();
        }
    }

    @GetMapping
    public Result<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "1") long page,
                                 @RequestParam(defaultValue = "10") long size) {
        requireAdmin();
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<User>lambdaQuery()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(User::getUsername, keyword)
                                .or().like(User::getNickname, keyword))
                        .orderByAsc(User::getId));
        p.getRecords().forEach(u -> u.setPassword(null));
        return Result.ok(PageResult.from(p));
    }

    /** 下拉选择用，不分页 */
    @GetMapping("/simple")
    public Result<List<User>> simple() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, 1).orderByAsc(User::getId));
        users.forEach(u -> u.setPassword(null));
        return Result.ok(users);
    }

    @PostMapping
    public Result<User> create(@RequestBody UserUpsertReq req) {
        requireAdmin();
        Long count = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, req.getUsername()));
        if (count > 0) {
            throw new ApiException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setRole(req.getRole());
        user.setStatus(1);
        userMapper.insert(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateReq req) {
        requireAdmin();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.notFound("用户");
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getPhone() != null) {
            user.setPhone(req.getPhone());
        }
        if (req.getRole() != null) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            if (id == 1 && req.getStatus() != 1) {
                throw new ApiException("不能禁用超级管理员");
            }
            user.setStatus(req.getStatus());
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody PasswordReq req) {
        requireAdmin();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.notFound("用户");
        }
        user.setPassword(encoder.encode(req.getPassword()));
        userMapper.updateById(user);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        if (id == 1) {
            throw new ApiException("不能删除超级管理员");
        }
        userMapper.deleteById(id);
        return Result.ok();
    }
}
