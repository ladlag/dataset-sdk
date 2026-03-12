package com.knowledge.sdk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.util.JsonUtil;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);

    private final KnowledgeProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String token;
    private volatile long expireTime;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ConcurrentHashMap<String, TokenEntry> userTokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    private volatile PublicKey cachedPublicKey;

    public TokenManager(KnowledgeProperties properties, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Get the token for the default user (configured via properties).
     */
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

    /**
     * Get a token for a specific user. Logs in as that user via SSO
     * and caches the token per user key.
     *
     * @param username the user's username
     * @param email    the user's email
     * @return access token for this user
     */
    public String getTokenForUser(String username, String email) {
        if (username == null || email == null) {
            throw new KnowledgeException("Username and email must not be null for per-user token");
        }

        String cacheKey = buildCacheKey(username, email);

        TokenEntry entry = userTokenCache.get(cacheKey);
        if (entry != null && System.currentTimeMillis() < entry.expireTime) {
            return entry.token;
        }

        return refreshTokenForUser(username, email);
    }

    public String refreshToken() {
        lock.writeLock().lock();
        try {
            if (token != null && System.currentTimeMillis() < expireTime) {
                return token;
            }

            log.info("Refreshing access token via SSO login for default user");
            String accessToken = doSsoLogin(properties.getUsername(), properties.getEmail());

            this.token = accessToken;
            this.expireTime = System.currentTimeMillis()
                    + (properties.getTokenTtlSeconds() - properties.getTokenExpiryBufferSeconds()) * 1000;

            log.debug("Default user token obtained: {}", maskToken(this.token));
            return this.token;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Refresh token for a specific user.
     */
    public String refreshTokenForUser(String username, String email) {
        String cacheKey = buildCacheKey(username, email);
        Object userLock = userLocks.computeIfAbsent(cacheKey, k -> new Object());

        synchronized (userLock) {
            TokenEntry entry = userTokenCache.get(cacheKey);
            if (entry != null && System.currentTimeMillis() < entry.expireTime) {
                return entry.token;
            }

            log.info("Refreshing access token via SSO login for user: {}", username);
            String accessToken = doSsoLogin(username, email);

            long expiry = System.currentTimeMillis()
                    + (properties.getTokenTtlSeconds() - properties.getTokenExpiryBufferSeconds()) * 1000;
            userTokenCache.put(cacheKey, new TokenEntry(accessToken, expiry));

            log.debug("User '{}' token obtained: {}", username, maskToken(accessToken));
            return accessToken;
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

    /**
     * Invalidate token for a specific user.
     */
    public void invalidateTokenForUser(String username, String email) {
        if (username == null || email == null) {
            return;
        }
        String cacheKey = buildCacheKey(username, email);
        userTokenCache.remove(cacheKey);
    }

    private String doSsoLogin(String username, String email) {
        try {
            String userInfoJson = buildUserInfoJson(username, email);
            String encryptedUserInfo = encryptUserInfo(userInfoJson);

            String url = properties.getBaseUrl() + properties.getSsoLoginPath();
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
                log.info("SSO login request: url={}, user={}, time={}ms, status={}", url, username, elapsed, response.code());

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

                log.debug("SSO login successful for user '{}', obtained token: {}", username, maskToken(accessToken));
                return accessToken;
            }
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh token for user: {}", username, e);
            throw new KnowledgeException("Failed to refresh token", e);
        }
    }

    private String buildUserInfoJson(String username, String email) {
        return "{\"username\":\"" + escapeJson(username) + "\",\"email\":\"" + escapeJson(email) + "\"}";
    }

    private String escapeJson(String value) {
        return JsonUtil.escapeJson(value);
    }

    private String encryptUserInfo(String userInfo) {
        try {
            PublicKey publicKey = getPublicKey();
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(userInfo.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to encrypt user info", e);
        }
    }

    private PublicKey getPublicKey() throws Exception {
        if (cachedPublicKey != null) {
            return cachedPublicKey;
        }
        synchronized (this) {
            if (cachedPublicKey != null) {
                return cachedPublicKey;
            }
            cachedPublicKey = loadPublicKey();
            return cachedPublicKey;
        }
    }

    private PublicKey loadPublicKey() throws Exception {
        String keyPath = properties.getRsaPublicKeyPath();
        InputStream is = getClass().getClassLoader().getResourceAsStream(keyPath);
        if (is == null) {
            throw new KnowledgeException("RSA public key file not found in classpath: " + keyPath);
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

    private String buildCacheKey(String username, String email) {
        return username + "|" + email;
    }

    private String maskToken(String token) {
        if (token == null) return "null";
        if (token.length() <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private static class TokenEntry {
        final String token;
        final long expireTime;

        TokenEntry(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }
    }
}
