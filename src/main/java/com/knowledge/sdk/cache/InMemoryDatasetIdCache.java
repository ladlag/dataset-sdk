package com.knowledge.sdk.cache;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of {@link DatasetIdCache}.
 *
 * <p>Uses a {@link ConcurrentHashMap} to store dataset IDs in process memory.
 * This implementation is thread-safe and suitable for single-instance deployments.
 *
 * <p>Limitations:
 * <ul>
 *   <li>Cache is lost on application restart</li>
 *   <li>Not shared across multiple application instances</li>
 * </ul>
 *
 * <p>For multi-instance deployments, add {@code spring-boot-starter-data-redis}
 * to your classpath. The SDK will automatically use Redis-backed caching.
 */
public class InMemoryDatasetIdCache implements DatasetIdCache {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String get(String datasetName) {
        return cache.get(datasetName);
    }

    @Override
    public void put(String datasetName, String datasetId) {
        cache.put(datasetName, datasetId);
    }

    @Override
    public void removeByValue(String datasetId) {
        cache.entrySet().removeIf(entry -> datasetId.equals(entry.getValue()));
    }
}
