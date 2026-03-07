package com.knowledge.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.client.KnowledgeHttpClient;
import com.knowledge.sdk.service.KnowledgeDatasetService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeAutoConfiguration {

    @Bean("knowledgeSdkOkHttpClient")
    @ConditionalOnMissingBean(name = "knowledgeSdkOkHttpClient")
    public OkHttpClient knowledgeSdkOkHttpClient(KnowledgeProperties properties) {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(20);

        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    @Bean("knowledgeSdkObjectMapper")
    @ConditionalOnMissingBean(name = "knowledgeSdkObjectMapper")
    public ObjectMapper knowledgeSdkObjectMapper() {
        return new ObjectMapper();
    }

    @Bean("knowledgeSdkTokenManager")
    @ConditionalOnMissingBean(name = "knowledgeSdkTokenManager")
    public TokenManager knowledgeSdkTokenManager(KnowledgeProperties properties,
                                                  OkHttpClient knowledgeSdkOkHttpClient,
                                                  ObjectMapper knowledgeSdkObjectMapper) {
        return new TokenManager(properties, knowledgeSdkOkHttpClient, knowledgeSdkObjectMapper);
    }

    @Bean("knowledgeSdkHttpClient")
    @ConditionalOnMissingBean(name = "knowledgeSdkHttpClient")
    public KnowledgeHttpClient knowledgeSdkHttpClient(KnowledgeProperties properties,
                                                       TokenManager knowledgeSdkTokenManager,
                                                       ObjectMapper knowledgeSdkObjectMapper,
                                                       OkHttpClient knowledgeSdkOkHttpClient) {
        return new KnowledgeHttpClient(properties, knowledgeSdkTokenManager, knowledgeSdkObjectMapper, knowledgeSdkOkHttpClient);
    }

    @Bean("knowledgeDatasetService")
    @ConditionalOnMissingBean(name = "knowledgeDatasetService")
    public KnowledgeDatasetService knowledgeDatasetService(KnowledgeHttpClient knowledgeSdkHttpClient,
                                                            KnowledgeProperties properties) {
        return new KnowledgeDatasetService(knowledgeSdkHttpClient, properties);
    }
}
