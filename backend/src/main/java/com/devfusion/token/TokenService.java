package com.devfusion.token;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.token.mapper.AccessTokenMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/** 个人访问令牌：明文仅创建时返回一次，库内存 SHA-256 */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String PREFIX = "dfpat_";

    private final AccessTokenMapper tokenMapper;
    private final SecureRandom random = new SecureRandom();

    /** 生成新令牌明文并落库（哈希） */
    public AccessToken issue(Long userId, String name) {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String plain = PREFIX + HexFormat.of().formatHex(buf);
        AccessToken t = new AccessToken();
        t.setUserId(userId);
        t.setName(name);
        t.setTokenHash(hash(plain));
        tokenMapper.insert(t);
        t.setTokenHash(plain); // 仅本次返回明文
        return t;
    }

    /** 校验明文令牌，命中返回记录（并更新 last_used_at），否则 null */
    public AccessToken verify(String plain) {
        if (plain == null || !plain.startsWith(PREFIX)) {
            return null;
        }
        AccessToken t = tokenMapper.selectOne(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getTokenHash, hash(plain)).last("LIMIT 1"));
        if (t == null) {
            return null;
        }
        if (t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        t.setLastUsedAt(LocalDateTime.now());
        tokenMapper.updateById(t);
        return t;
    }

    public List<AccessToken> listMine(Long userId) {
        return tokenMapper.selectList(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getUserId, userId).orderByDesc(AccessToken::getId));
    }

    public void revoke(Long userId, Long id) {
        tokenMapper.delete(Wrappers.<AccessToken>lambdaQuery()
                .eq(AccessToken::getId, id).eq(AccessToken::getUserId, userId));
    }

    public static String hash(String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
