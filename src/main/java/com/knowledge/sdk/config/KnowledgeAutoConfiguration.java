package com.knowledge.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.cache.InitFileIdCache;
import com.knowledge.sdk.cache.InMemoryInitFileIdCache;
import com.knowledge.sdk.client.KnowledgeHttpClient;
import com.knowledge.sdk.service.KnowledgeDatasetService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
@Import(KnowledgeRedisAutoConfiguration.class)
public class KnowledgeAutoConfiguration {

    /**
     * Internal ObjectMapper for SDK use only. NOT exposed as a Spring bean to avoid
     * blocking Spring Boot's auto-configured ObjectMapper (which provides JSR310
     * LocalDateTime support, spring.jackson.* properties, etc. to the consumer app).
     */
    private final ObjectMapper sdkObjectMapper = createSdkObjectMapper();

    private static ObjectMapper createSdkObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Bean("knowledgeSdkOkHttpClient")
    @ConditionalOnMissingBean(name = "knowledgeSdkOkHttpClient")
    public OkHttpClient knowledgeSdkOkHttpClient(KnowledgeProperties properties) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(properties.getMaxRequests());
        dispatcher.setMaxRequestsPerHost(properties.getMaxRequestsPerHost());

        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(
                        properties.getConnectionPoolSize(),
                        properties.getConnectionPoolKeepAliveMinutes(),
                        TimeUnit.MINUTES))
                .build();
    }

    @Bean("knowledgeSdkTokenManager")
    @ConditionalOnMissingBean(name = "knowledgeSdkTokenManager")
    public TokenManager knowledgeSdkTokenManager(KnowledgeProperties properties,
                                                  OkHttpClient knowledgeSdkOkHttpClient) {
        return new TokenManager(properties, knowledgeSdkOkHttpClient, sdkObjectMapper);
    }

    @Bean("knowledgeSdkInitFileIdCache")
    @ConditionalOnMissingBean(InitFileIdCache.class)
    public InitFileIdCache knowledgeSdkInitFileIdCache() {
        return new InMemoryInitFileIdCache();
    }

    @Bean("knowledgeSdkHttpClient")
    @ConditionalOnMissingBean(name = "knowledgeSdkHttpClient")
    public KnowledgeHttpClient knowledgeSdkHttpClient(KnowledgeProperties properties,
                                                       TokenManager knowledgeSdkTokenManager,
                                                       OkHttpClient knowledgeSdkOkHttpClient,
                                                       InitFileIdCache knowledgeSdkInitFileIdCache) {
        return new KnowledgeHttpClient(properties, knowledgeSdkTokenManager, sdkObjectMapper,
                knowledgeSdkOkHttpClient, knowledgeSdkInitFileIdCache);
    }

    @Bean("knowledgeDatasetService")
    @ConditionalOnMissingBean(name = "knowledgeDatasetService")
    public KnowledgeDatasetService knowledgeDatasetService(KnowledgeHttpClient knowledgeSdkHttpClient,
                                                            KnowledgeProperties properties) {
        return new KnowledgeDatasetService(knowledgeSdkHttpClient, properties);
    }
}
