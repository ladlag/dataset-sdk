package com.knowledge.sdk.cache;

/**
 * Cache interface for the shared init file ID used during dataset creation.
 *
 * <p>When creating a dataset, the API requires at least one file_id in data_source.
 * The SDK auto-uploads a small placeholder file and caches the returned file ID
 * to avoid re-uploading on subsequent dataset creations.
 *
 * <p>A single init file is shared across all users — there is no per-user init file.
 * The cache stores only one entry with key {@code "default"}.
 *
 * <p><b>Default behavior (single-instance):</b> The SDK provides an in-memory implementation
 * ({@link InMemoryInitFileIdCache}) backed by {@code ConcurrentHashMap}.
 *
 * <p><b>Multi-instance deployment:</b> Add {@code spring-boot-starter-data-redis} to your
 * classpath and configure Redis. The SDK will automatically use {@link RedisInitFileIdCache}
 * to share cache across instances. Redis TTL is configurable via {@code knowledge.cache-ttl-hours}
 * (default: 24 hours).
 *
 * <p><b>Custom implementation:</b> You can also provide your own implementation by
 * registering a bean of type {@code InitFileIdCache}. It will take precedence over
 * both the in-memory and Redis defaults.
 */
public interface InitFileIdCache {

    /**
     * Retrieve the cached init file ID.
     *
     * @param key cache key (always {@code "default"})
     * @return the cached file ID, or {@code null} if not cached
     */
    String get(String key);

    /**
     * Store the init file ID in the cache.
     *
     * @param key    cache key (always {@code "default"})
     * @param fileId the file ID returned by the upload API
     */
    void put(String key, String fileId);
}
