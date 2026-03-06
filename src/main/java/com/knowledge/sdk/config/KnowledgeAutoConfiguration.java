package com.knowledge.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.client.KnowledgeHttpClient;
import com.knowledge.sdk.service.KnowledgeService;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient knowledgeOkHttpClient(KnowledgeProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper knowledgeObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenManager tokenManager(KnowledgeProperties properties,
                                     OkHttpClient knowledgeOkHttpClient,
                                     ObjectMapper knowledgeObjectMapper) {
        return new TokenManager(properties, knowledgeOkHttpClient, knowledgeObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeHttpClient knowledgeHttpClient(KnowledgeProperties properties,
                                                    TokenManager tokenManager,
                                                    ObjectMapper knowledgeObjectMapper,
                                                    OkHttpClient knowledgeOkHttpClient) {
        return new KnowledgeHttpClient(properties, tokenManager, knowledgeObjectMapper, knowledgeOkHttpClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeService knowledgeService(KnowledgeHttpClient knowledgeHttpClient,
                                              KnowledgeProperties properties) {
        return new KnowledgeService(knowledgeHttpClient, properties);
    }
}
