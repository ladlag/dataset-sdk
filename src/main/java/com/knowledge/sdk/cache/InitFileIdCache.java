package com.knowledge.sdk.cache;

/**
 * Cache interface for init file IDs used during dataset creation.
 *
 * <p>When creating a dataset, the API requires at least one file_id in data_source.
 * The SDK auto-uploads a small placeholder file and caches the returned file ID
 * to avoid re-uploading on subsequent dataset creations.
 *
 * <p><b>Default behavior:</b> The SDK provides an in-memory implementation
 * ({@link InMemoryInitFileIdCache}) backed by {@code ConcurrentHashMap}.
 * This is sufficient for single-instance deployments.
 *
 * <p><b>For multi-instance / production deployments:</b> Implement this interface
 * with a Redis-backed (or other shared cache) implementation and register it as
 * a Spring bean. The SDK will automatically use your implementation instead of
 * the in-memory default (any bean of type {@code InitFileIdCache} takes precedence).
 *
 * <p>Example Redis implementation:
 * <pre>{@code
 * @Bean
 * public InitFileIdCache redisInitFileIdCache(StringRedisTemplate redisTemplate) {
 *     return new InitFileIdCache() {
 *         private static final String KEY_PREFIX = "knowledge:init-file:";
 *
 *         @Override
 *         public String get(String key) {
 *             return redisTemplate.opsForValue().get(KEY_PREFIX + key);
 *         }
 *
 *         @Override
 *         public void put(String key, String fileId) {
 *             redisTemplate.opsForValue().set(KEY_PREFIX + key, fileId, 24, TimeUnit.HOURS);
 *         }
 *     };
 * }
 * }</pre>
 *
 * <p>Cache keys follow the format:
 * <ul>
 *   <li>{@code "default"} — for the default (system-level) user</li>
 *   <li>{@code "username|email"} — for per-user datasets</li>
 * </ul>
 */
public interface InitFileIdCache {

    /**
     * Retrieve a cached init file ID.
     *
     * @param key cache key ({@code "default"} or {@code "username|email"})
     * @return the cached file ID, or {@code null} if not cached
     */
    String get(String key);

    /**
     * Store an init file ID in the cache.
     *
     * @param key    cache key ({@code "default"} or {@code "username|email"})
     * @param fileId the file ID returned by the upload API
     */
    void put(String key, String fileId);
}
