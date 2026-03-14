package com.knowledge.sdk.config;

import com.knowledge.sdk.cache.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for Redis-backed caches.
 *
 * <p>This configuration activates automatically when:
 * <ul>
 *   <li>{@code spring-boot-starter-data-redis} is on the classpath</li>
 *   <li>A {@code StringRedisTemplate} bean is available (i.e. Redis is configured)</li>
 * </ul>
 *
 * <p>It registers a Redis-backed implementation of {@link InitFileIdCache},
 * replacing the default in-memory implementation. Init file IDs are rarely-changing
 * data suitable for caching across multiple application instances.
 *
 * <p>To use in-memory caches instead, simply do not add {@code spring-boot-starter-data-redis}
 * to your classpath, or provide your own bean of type {@code InitFileIdCache}.
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
public class KnowledgeRedisAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRedisAutoConfiguration.class);

    @Bean("knowledgeSdkInitFileIdCache")
    @ConditionalOnMissingBean(InitFileIdCache.class)
    public InitFileIdCache redisInitFileIdCache(StringRedisTemplate redisTemplate,
                                                 KnowledgeProperties properties) {
        log.info("Using Redis-backed InitFileIdCache (TTL={}h)", properties.getCacheTtlHours());
        return new RedisInitFileIdCache(redisTemplate, properties.getCacheTtlHours());
    }
}
