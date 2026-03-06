package com.knowledge.sdk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private static final String SSO_LOGIN_PATH = "/tenant/api/app/account/sso_login";

    private final KnowledgeProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String token;
    private volatile long expireTime;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TokenManager(KnowledgeProperties properties, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String getToken() {
        lock.readLock().lock();
        try {
            if (token != null && System.currentTimeMillis() < expireTime) {
                return token;
            }
        } finally {
            lock.readLock().unlock();
        }

        return refreshToken();
    }

    public String refreshToken() {
        lock.writeLock().lock();
        try {
            if (token != null && System.currentTimeMillis() < expireTime) {
                return token;
            }

            log.info("Refreshing access token via SSO login");
            String encryptedUserInfo = encryptUserInfo(properties.getUserInfo());

            String url = properties.getBaseUrl() + SSO_LOGIN_PATH;
            String requestBody = objectMapper.writeValueAsString(
                    java.util.Collections.singletonMap("HTTP_USER_INFO", encryptedUserInfo)
            );

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + properties.getSystemToken())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            long startTime = System.currentTimeMillis();
            try (Response response = httpClient.newCall(request).execute()) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("SSO login request: url={}, time={}ms, status={}", url, elapsed, response.code());

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("SSO login failed: status={}, body={}", response.code(), errorBody);
                    throw new KnowledgeException("SSO login failed with status " + response.code(), response.code());
                }

                String body = response.body() != null ? response.body().string() : "";
                JsonNode jsonNode = objectMapper.readTree(body);

                JsonNode dataNode = jsonNode.has("data") ? jsonNode.get("data") : jsonNode;
                String accessToken = dataNode.has("access_token") ? dataNode.get("access_token").asText() : null;

                if (accessToken == null || accessToken.isEmpty()) {
                    throw new KnowledgeException("SSO login response missing access_token");
                }

                this.token = accessToken;
                this.expireTime = System.currentTimeMillis()
                        + (3600 - properties.getTokenExpiryBufferSeconds()) * 1000;

                log.info("Access token refreshed successfully");
                return this.token;
            }
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh token", e);
            throw new KnowledgeException("Failed to refresh token", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void invalidateToken() {
        lock.writeLock().lock();
        try {
            this.token = null;
            this.expireTime = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String encryptUserInfo(String userInfo) {
        try {
            PublicKey publicKey = loadPublicKey();
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(userInfo.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to encrypt user info", e);
        }
    }

    private PublicKey loadPublicKey() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("rsa_public_key.pem");
        if (is == null) {
            throw new KnowledgeException("RSA public key file not found in classpath");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("-----")) {
                    sb.append(line);
                }
            }
        }

        byte[] keyBytes = Base64.getDecoder().decode(sb.toString());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
