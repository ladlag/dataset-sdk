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
     * Username for SSO login (part of HTTP_USER_INFO JSON: {"username":"...","email":"..."})
     */
    private String username;

    /**
     * Email for SSO login (part of HTTP_USER_INFO JSON: {"username":"...","email":"..."})
     */
    private String email;

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

    /**
     * Classpath location of the RSA public key PEM file used to encrypt HTTP_USER_INFO
     * for SSO login. Default: rsa_public_key.pem (bundled with SDK).
     * To use a custom key, place your PEM file on the classpath and set this property.
     */
    private String rsaPublicKeyPath = "rsa_public_key.pem";

    // ===== API path configuration =====

    /**
     * SSO login endpoint path (appended to baseUrl)
     */
    private String ssoLoginPath = "/tenant/api/app/account/sso_login";

    /**
     * Token TTL in seconds. SDK will refresh the token before it expires
     * (accounting for tokenExpiryBufferSeconds).
     */
    private long tokenTtlSeconds = 3600;

    /**
     * Full path for dataset init endpoint (create dataset, optionally with documents).
     * Appended to baseUrl. Requires data_source in request body.
     */
    private String datasetInitPath = "/console/api/datasets/init";

    /**
     * Full path for file upload endpoint.
     * Appended to baseUrl. Query parameter ?source=datasets is added automatically.
     */
    private String fileUploadPath = "/console/api/files/upload";

    /**
     * Full path for listing datasets.
     * Appended to baseUrl. Query parameters (page, limit, keyword) are added automatically.
     */
    private String datasetListPath = "/console/api/datasets";

    /**
     * Path pattern for single dataset operations (e.g. delete dataset).
     * {datasetId} is replaced at runtime.
     */
    private String datasetByIdPath = "/console/api/datasets/{datasetId}";

    /**
     * Path pattern for documents in a dataset (e.g. create documents).
     * {datasetId} is replaced at runtime.
     */
    private String datasetDocumentsPath = "/console/api/datasets/{datasetId}/documents";

    /**
     * Path pattern for a single document in a dataset (e.g. delete document).
     * {datasetId} and {documentId} are replaced at runtime.
     */
    private String datasetDocumentByIdPath = "/console/api/datasets/{datasetId}/documents/{documentId}";

    // ===== OkHttp connection pool configuration =====

    /**
     * Maximum number of total concurrent OkHttp requests
     */
    private int maxRequests = 64;

    /**
     * Maximum number of concurrent OkHttp requests per host
     */
    private int maxRequestsPerHost = 20;

    /**
     * Number of idle connections to keep in the connection pool
     */
    private int connectionPoolSize = 20;

    /**
     * Keep-alive duration for idle connections in minutes
     */
    private int connectionPoolKeepAliveMinutes = 5;

    // ===== Document processing configuration =====

    /**
     * Indexing technique for document processing
     */
    private String indexingTechnique = "high_quality";

    /**
     * Document form type
     */
    private String docForm = "text_model";

    /**
     * Document language
     */
    private String docLanguage = "English";

    /**
     * Process rule mode
     */
    private String processRuleMode = "custom";

    /**
     * Segment separator for document chunking (default: two newlines)
     */
    private String segmentSeparator = "\n\n";

    /**
     * Maximum tokens per segment
     */
    private int segmentMaxTokens = 500;

    /**
     * Chunk overlap tokens between segments
     */
    private int segmentChunkOverlap = 50;

    /**
     * Search method for retrieval model
     */
    private String searchMethod = "hybrid_search";

    /**
     * Top-K results for retrieval
     */
    private int topK = 3;

    /**
     * Whether score threshold is enabled for retrieval
     */
    private boolean scoreThresholdEnabled = false;

    /**
     * Score threshold value for retrieval
     */
    private double scoreThreshold = 0.5;

    /**
     * Whether reranking is enabled
     */
    private boolean rerankingEnable = true;

    /**
     * Reranking mode
     */
    private String rerankingMode = "reranking_model";

    /**
     * Reranking model provider name
     */
    private String rerankingProviderName = "langgenius/tongyi/tongyi";

    /**
     * Reranking model name
     */
    private String rerankingModelName = "gte-rerank-v2";

    /**
     * Weight type for retrieval
     */
    private String weightType = "customized";

    /**
     * Vector weight for hybrid search.
     * Note: vectorWeight and keywordWeight should sum to 1.0.
     */
    private double vectorWeight = 0.7;

    /**
     * Keyword weight for hybrid search.
     * Note: vectorWeight and keywordWeight should sum to 1.0.
     */
    private double keywordWeight = 0.3;

    /**
     * Embedding model name
     */
    private String embeddingModel = "text-embedding-v2";

    /**
     * Embedding model provider
     */
    private String embeddingModelProvider = "langgenius/tongyi/tongyi";

    /**
     * File name used for the placeholder/init file uploaded when creating a dataset.
     * The API requires at least one file_id in data_source, so the SDK auto-uploads
     * this small text file before creating the dataset.
     */
    private String initFileName = "init.txt";

    /**
     * Content of the placeholder/init file uploaded when creating a dataset.
     * Should be a small, generic text suitable as an initial document.
     */
    private String initFileContent = "Knowledge Base Initialization File";

    // ===== Cache configuration =====

    /**
     * Cache TTL in hours for the Redis-backed init file ID cache.
     * Only applies when spring-boot-starter-data-redis is on the classpath.
     * Default: 24 hours.
     */
    private long cacheTtlHours = 24;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getRsaPublicKeyPath() {
        return rsaPublicKeyPath;
    }

    public void setRsaPublicKeyPath(String rsaPublicKeyPath) {
        this.rsaPublicKeyPath = rsaPublicKeyPath;
    }

    public String getSsoLoginPath() {
        return ssoLoginPath;
    }

    public void setSsoLoginPath(String ssoLoginPath) {
        this.ssoLoginPath = ssoLoginPath;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public String getDatasetInitPath() {
        return datasetInitPath;
    }

    public void setDatasetInitPath(String datasetInitPath) {
        this.datasetInitPath = datasetInitPath;
    }

    public String getFileUploadPath() {
        return fileUploadPath;
    }

    public void setFileUploadPath(String fileUploadPath) {
        this.fileUploadPath = fileUploadPath;
    }

    public String getDatasetListPath() {
        return datasetListPath;
    }

    public void setDatasetListPath(String datasetListPath) {
        this.datasetListPath = datasetListPath;
    }

    public String getDatasetByIdPath() {
        return datasetByIdPath;
    }

    public void setDatasetByIdPath(String datasetByIdPath) {
        this.datasetByIdPath = datasetByIdPath;
    }

    public String getDatasetDocumentsPath() {
        return datasetDocumentsPath;
    }

    public void setDatasetDocumentsPath(String datasetDocumentsPath) {
        this.datasetDocumentsPath = datasetDocumentsPath;
    }

    public String getDatasetDocumentByIdPath() {
        return datasetDocumentByIdPath;
    }

    public void setDatasetDocumentByIdPath(String datasetDocumentByIdPath) {
        this.datasetDocumentByIdPath = datasetDocumentByIdPath;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public int getMaxRequestsPerHost() {
        return maxRequestsPerHost;
    }

    public void setMaxRequestsPerHost(int maxRequestsPerHost) {
        this.maxRequestsPerHost = maxRequestsPerHost;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public int getConnectionPoolKeepAliveMinutes() {
        return connectionPoolKeepAliveMinutes;
    }

    public void setConnectionPoolKeepAliveMinutes(int connectionPoolKeepAliveMinutes) {
        this.connectionPoolKeepAliveMinutes = connectionPoolKeepAliveMinutes;
    }

    public String getIndexingTechnique() {
        return indexingTechnique;
    }

    public void setIndexingTechnique(String indexingTechnique) {
        this.indexingTechnique = indexingTechnique;
    }

    public String getDocForm() {
        return docForm;
    }

    public void setDocForm(String docForm) {
        this.docForm = docForm;
    }

    public String getDocLanguage() {
        return docLanguage;
    }

    public void setDocLanguage(String docLanguage) {
        this.docLanguage = docLanguage;
    }

    public String getProcessRuleMode() {
        return processRuleMode;
    }

    public void setProcessRuleMode(String processRuleMode) {
        this.processRuleMode = processRuleMode;
    }

    public String getSegmentSeparator() {
        return segmentSeparator;
    }

    public void setSegmentSeparator(String segmentSeparator) {
        this.segmentSeparator = segmentSeparator;
    }

    public int getSegmentMaxTokens() {
        return segmentMaxTokens;
    }

    public void setSegmentMaxTokens(int segmentMaxTokens) {
        this.segmentMaxTokens = segmentMaxTokens;
    }

    public int getSegmentChunkOverlap() {
        return segmentChunkOverlap;
    }

    public void setSegmentChunkOverlap(int segmentChunkOverlap) {
        this.segmentChunkOverlap = segmentChunkOverlap;
    }

    public String getSearchMethod() {
        return searchMethod;
    }

    public void setSearchMethod(String searchMethod) {
        this.searchMethod = searchMethod;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isScoreThresholdEnabled() {
        return scoreThresholdEnabled;
    }

    public void setScoreThresholdEnabled(boolean scoreThresholdEnabled) {
        this.scoreThresholdEnabled = scoreThresholdEnabled;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public boolean isRerankingEnable() {
        return rerankingEnable;
    }

    public void setRerankingEnable(boolean rerankingEnable) {
        this.rerankingEnable = rerankingEnable;
    }

    public String getRerankingMode() {
        return rerankingMode;
    }

    public void setRerankingMode(String rerankingMode) {
        this.rerankingMode = rerankingMode;
    }

    public String getRerankingProviderName() {
        return rerankingProviderName;
    }

    public void setRerankingProviderName(String rerankingProviderName) {
        this.rerankingProviderName = rerankingProviderName;
    }

    public String getRerankingModelName() {
        return rerankingModelName;
    }

    public void setRerankingModelName(String rerankingModelName) {
        this.rerankingModelName = rerankingModelName;
    }

    public String getWeightType() {
        return weightType;
    }

    public void setWeightType(String weightType) {
        this.weightType = weightType;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getEmbeddingModelProvider() {
        return embeddingModelProvider;
    }

    public void setEmbeddingModelProvider(String embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public String getInitFileName() {
        return initFileName;
    }

    public void setInitFileName(String initFileName) {
        this.initFileName = initFileName;
    }

    public String getInitFileContent() {
        return initFileContent;
    }

    public void setInitFileContent(String initFileContent) {
        this.initFileContent = initFileContent;
    }

    public long getCacheTtlHours() {
        return cacheTtlHours;
    }

    public void setCacheTtlHours(long cacheTtlHours) {
        this.cacheTtlHours = cacheTtlHours;
    }
}
