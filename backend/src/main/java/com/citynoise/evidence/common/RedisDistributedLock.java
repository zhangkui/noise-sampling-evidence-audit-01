package com.citynoise.evidence.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis SET NX 的分布式锁，用于并发提交相同记录的串行化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final String KEY_PREFIX = "noise:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试获取锁。
     *
     * @return true 表示获取成功；false 表示已被占用
     */
    public boolean tryLock(String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String key) {
        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("释放分布式锁失败 key={}", key, e);
        }
    }

    /**
     * 在锁内执行；获取不到锁直接抛出并发冲突异常，不阻塞等待。
     * key 由调用方按业务指纹给出（如 record:{sensor}:{time}:{hash}、batch-create:{sensor}）。
     */
    public <T> T executeWithLock(String key, Duration ttl, java.util.function.Supplier<T> action) {
        if (!tryLock(key, ttl)) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        try {
            return action.get();
        } finally {
            unlock(key);
        }
    }
}
