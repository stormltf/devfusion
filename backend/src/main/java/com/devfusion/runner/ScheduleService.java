package com.devfusion.runner;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devfusion.pipeline.Pipeline;
import com.devfusion.pipeline.mapper.PipelineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/** 定时触发：每分钟扫描 trigger_type=SCHEDULE 的流水线，到点触发；Redis 锁防重复 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleService {

    private static final String LAST_KEY = "df:cron:last:";
    private static final String LOCK_KEY = "df:cron:lock:";

    private final PipelineMapper pipelineMapper;
    private final RunService runService;
    private final StringRedisTemplate redis;

    @Scheduled(fixedRate = 60_000L, initialDelay = 30_000L)
    public void tick() {
        for (Pipeline p : pipelineMapper.selectList(Wrappers.<Pipeline>lambdaQuery()
                .eq(Pipeline::getTriggerType, "SCHEDULE")
                .eq(Pipeline::getStatus, "ENABLED"))) {
            if (p.getCron() == null || p.getCron().isBlank()) {
                continue;
            }
            CronExpression cron;
            try {
                cron = CronExpression.parse(p.getCron());
            } catch (Exception e) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last = lastFire(p.getId());
            LocalDateTime next = cron.next(last == null ? toStart(p) : last);
            if (next == null || next.isAfter(now)) {
                continue;
            }
            Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY + p.getId(), "1", Duration.ofMinutes(2));
            if (locked == null || !locked) {
                continue;
            }
            try {
                redis.opsForValue().set(LAST_KEY + p.getId(), now.toString());
                log.info("scheduled trigger pipeline {} -> run", p.getId());
                runService.trigger(p.getId(), "SCHEDULE", null);
            } catch (Exception e) {
                log.warn("scheduled trigger pipeline {} failed: {}", p.getId(), e.getMessage());
            } finally {
                redis.delete(LOCK_KEY + p.getId());
            }
        }
    }

    private LocalDateTime toStart(Pipeline p) {
        return p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.now().minusMinutes(1);
    }

    private LocalDateTime lastFire(Long pipelineId) {
        String v = redis.opsForValue().get(LAST_KEY + pipelineId);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v);
        } catch (Exception e) {
            return null;
        }
    }
}
