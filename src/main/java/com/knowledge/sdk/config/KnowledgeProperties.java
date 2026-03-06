package com.knowledge.sdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeProperties {

    /**
     * Base URL of the knowledge system (e.g. https://host:port)
     */
    private String baseUrl;

    /**
     * System token used for SSO login Authorization header
     */
    private String systemToken;

    /**
     * User info for SSO login (will be RSA-encrypted)
     */
    private String userInfo;

    /**
     * Token expiry buffer in seconds (refresh token before it actually expires)
     */
    private long tokenExpiryBufferSeconds = 300;

    /**
     * OkHttp connect timeout in seconds
     */
    private int connectTimeoutSeconds = 30;

    /**
     * OkHttp read timeout in seconds
     */
    private int readTimeoutSeconds = 60;

    /**
     * OkHttp write timeout in seconds
     */
    private int writeTimeoutSeconds = 60;

    /**
     * Maximum file size in bytes (20MB)
     */
    private long maxFileSize = 20 * 1024 * 1024;

    /**
     * Maximum files per upload batch
     */
    private int maxFilesPerBatch = 10;

    /**
     * Public dataset name
     */
    private String publicDatasetName = "public_dataset";

    /**
     * User dataset name prefix
     */
    private String userDatasetPrefix = "user_";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSystemToken() {
        return systemToken;
    }

    public void setSystemToken(String systemToken) {
        this.systemToken = systemToken;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
    }

    public long getTokenExpiryBufferSeconds() {
        return tokenExpiryBufferSeconds;
    }

    public void setTokenExpiryBufferSeconds(long tokenExpiryBufferSeconds) {
        this.tokenExpiryBufferSeconds = tokenExpiryBufferSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        this.writeTimeoutSeconds = writeTimeoutSeconds;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxFilesPerBatch() {
        return maxFilesPerBatch;
    }

    public void setMaxFilesPerBatch(int maxFilesPerBatch) {
        this.maxFilesPerBatch = maxFilesPerBatch;
    }

    public String getPublicDatasetName() {
        return publicDatasetName;
    }

    public void setPublicDatasetName(String publicDatasetName) {
        this.publicDatasetName = publicDatasetName;
    }

    public String getUserDatasetPrefix() {
        return userDatasetPrefix;
    }

    public void setUserDatasetPrefix(String userDatasetPrefix) {
        this.userDatasetPrefix = userDatasetPrefix;
    }
}
