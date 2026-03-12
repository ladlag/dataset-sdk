package com.knowledge.sdk.cache;

/**
 * Cache interface for dataset IDs indexed by dataset name.
 *
 * <p>When uploading documents, the SDK first checks if a dataset with the given
 * name already exists. The result is cached to avoid repeated list-datasets API calls.
 *
 * <p><b>Default behavior:</b> The SDK provides an in-memory implementation
 * ({@link InMemoryDatasetIdCache}) backed by {@code ConcurrentHashMap}.
 * This is sufficient for single-instance deployments.
 *
 * <p><b>For multi-instance / production deployments:</b> When
 * {@code spring-boot-starter-data-redis} is on the classpath and a
 * {@code StringRedisTemplate} bean is available, the SDK automatically
 * uses a Redis-backed implementation. You can also provide your own
 * by registering a bean of type {@code DatasetIdCache}.
 *
 * <p>Cache keys are dataset names (e.g. {@code "user_alice001"}, {@code "public_dataset"}).
 */
public interface DatasetIdCache {

    /**
     * Retrieve a cached dataset ID by dataset name.
     *
     * @param datasetName the dataset name
     * @return the cached dataset ID, or {@code null} if not cached
     */
    String get(String datasetName);

    /**
     * Store a dataset ID in the cache.
     *
     * @param datasetName the dataset name
     * @param datasetId   the dataset ID
     */
    void put(String datasetName, String datasetId);

    /**
     * Remove a dataset from the cache by its ID.
     * Implementations should remove all entries whose value equals the given datasetId.
     *
     * @param datasetId the dataset ID to remove
     */
    void removeByValue(String datasetId);
}
