package com.knowledge.sdk.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link InitFileIdCache}.
 *
 * <p>Stores init file IDs in Redis, enabling cache sharing across
 * multiple application instances. Entries expire after a configurable TTL
 * (default: 24 hours).
 *
 * <p>Redis key format: {@code knowledge:init-file:{cacheKey}}
 */
public class RedisInitFileIdCache implements InitFileIdCache {

    private static final Logger log = LoggerFactory.getLogger(RedisInitFileIdCache.class);
    private static final String KEY_PREFIX = "knowledge:init-file:";

    private final StringRedisTemplate redisTemplate;
    private final long ttlHours;

    public RedisInitFileIdCache(StringRedisTemplate redisTemplate, long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
    }

    @Override
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("Failed to get init file ID from Redis for key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, String fileId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + key, fileId, ttlHours, TimeUnit.HOURS);
            log.debug("Cached init file ID in Redis: key='{}', fileId='{}'", key, fileId);
        } catch (Exception e) {
            log.warn("Failed to cache init file ID in Redis for key '{}': {}", key, e.getMessage());
        }
    }
}
