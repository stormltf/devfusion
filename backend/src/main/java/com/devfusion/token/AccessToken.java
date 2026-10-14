package com.devfusion.token;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 个人访问令牌（明文仅创建时返回一次，库内存 SHA-256 哈希） */
@Data
@TableName("access_token")
public class AccessToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String tokenHash;

    private LocalDateTime lastUsedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
