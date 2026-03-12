package com.knowledge.sdk.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link DatasetIdCache}.
 *
 * <p>Stores dataset IDs in Redis, enabling cache sharing across
 * multiple application instances. Entries expire after a configurable TTL
 * (default: 24 hours).
 *
 * <p>Uses two Redis structures:
 * <ul>
 *   <li>String key {@code knowledge:dataset:{name}} → dataset ID (for name→ID lookup)</li>
 *   <li>Set key {@code knowledge:dataset-reverse:{id}} → dataset names (for reverse lookup on delete)</li>
 * </ul>
 */
public class RedisDatasetIdCache implements DatasetIdCache {

    private static final Logger log = LoggerFactory.getLogger(RedisDatasetIdCache.class);
    private static final String KEY_PREFIX = "knowledge:dataset:";
    private static final String REVERSE_KEY_PREFIX = "knowledge:dataset-reverse:";

    private final StringRedisTemplate redisTemplate;
    private final long ttlHours;

    public RedisDatasetIdCache(StringRedisTemplate redisTemplate, long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours;
    }

    @Override
    public String get(String datasetName) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + datasetName);
        } catch (Exception e) {
            log.warn("Failed to get dataset ID from Redis for '{}': {}", datasetName, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String datasetName, String datasetId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + datasetName, datasetId, ttlHours, TimeUnit.HOURS);
            redisTemplate.opsForSet().add(REVERSE_KEY_PREFIX + datasetId, datasetName);
            redisTemplate.expire(REVERSE_KEY_PREFIX + datasetId, ttlHours, TimeUnit.HOURS);
            log.debug("Cached dataset ID in Redis: name='{}', id='{}'", datasetName, datasetId);
        } catch (Exception e) {
            log.warn("Failed to cache dataset ID in Redis for '{}': {}", datasetName, e.getMessage());
        }
    }

    @Override
    public void removeByValue(String datasetId) {
        try {
            Set<String> names = redisTemplate.opsForSet().members(REVERSE_KEY_PREFIX + datasetId);
            if (names != null) {
                for (String name : names) {
                    redisTemplate.delete(KEY_PREFIX + name);
                }
            }
            redisTemplate.delete(REVERSE_KEY_PREFIX + datasetId);
            log.debug("Removed dataset cache entries from Redis for id='{}'", datasetId);
        } catch (Exception e) {
            log.warn("Failed to remove dataset cache from Redis for id '{}': {}", datasetId, e.getMessage());
        }
    }
}
