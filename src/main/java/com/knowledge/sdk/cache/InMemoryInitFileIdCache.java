package com.knowledge.sdk.cache;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of {@link InitFileIdCache}.
 *
 * <p>Uses a {@link ConcurrentHashMap} to store init file IDs in process memory.
 * This implementation is thread-safe and suitable for single-instance deployments.
 *
 * <p>Limitations:
 * <ul>
 *   <li>Cache is lost on application restart (init files will be re-uploaded)</li>
 *   <li>Not shared across multiple application instances</li>
 * </ul>
 *
 * <p>For multi-instance deployments, provide a Redis-backed implementation
 * of {@link InitFileIdCache} as a Spring bean. See {@link InitFileIdCache} Javadoc
 * for an example.
 */
public class InMemoryInitFileIdCache implements InitFileIdCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        return cache.get(key);
    }

    @Override
    public void put(String key, String fileId) {
        cache.put(key, fileId);
    }
}
