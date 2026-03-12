package com.knowledge.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.cache.InitFileIdCache;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.model.*;
import com.knowledge.sdk.util.JsonUtil;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHttpClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final KnowledgeProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;
    private final InitFileIdCache initFileIdCache;

    public KnowledgeHttpClient(KnowledgeProperties properties, TokenManager tokenManager,
                               ObjectMapper objectMapper, OkHttpClient httpClient,
                               InitFileIdCache initFileIdCache) {
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.initFileIdCache = initFileIdCache;
    }

    // ===== Default user methods (using default token) =====

    public DatasetResponse createDataset(String name) {
        return createDataset(name, null, null);
    }

    public FileUploadResponse uploadFile(MultipartFile file) {
        return uploadFile(file, null, null);
    }

    public List<FileUploadResponse> uploadFiles(List<MultipartFile> files) {
        return uploadFiles(files, null, null);
    }

    public String createDocumentInDataset(String datasetId, List<String> fileIds) {
        return createDocumentInDataset(datasetId, fileIds, null, null);
    }

    public String initDatasetWithDocuments(String datasetName, List<String> fileIds) {
        return initDatasetWithDocuments(datasetName, fileIds, null, null);
    }

    public DatasetListResponse listDatasets(String keyword, int page, int limit) {
        return listDatasets(keyword, page, limit, null, null);
    }

    public void deleteDocument(String datasetId, String documentId) {
        deleteDocument(datasetId, documentId, null, null);
    }

    public void deleteDataset(String datasetId) {
        deleteDataset(datasetId, null, null);
    }

    // ===== Per-user methods (using user-specific token) =====

    public DatasetResponse createDataset(String name, String username, String email) {
        String initFileId = getOrUploadInitFile();
        String url = properties.getBaseUrl() + properties.getDatasetInitPath();

        List<String> fileIds = new ArrayList<>();
        fileIds.add(initFileId);
        String body = buildInitDatasetBody(name, fileIds);

        String responseBody = executeWithRetry("POST", url, body, username, email);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("dataset")) {
                return objectMapper.treeToValue(root.get("dataset"), DatasetResponse.class);
            }
            return objectMapper.readValue(responseBody, DatasetResponse.class);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse create dataset response", e);
        }
    }

    /**
     * Get a cached init file ID or upload a new placeholder file.
     * The init file is a small text file used to satisfy the API requirement
     * that data_source.file_ids must not be empty when creating a dataset.
     *
     * <p>A single init file is shared across all users (uploaded with the default token).
     * The init file is just a placeholder — it doesn't need to be per-user.
     *
     * @return the uploaded file ID
     */
    private String getOrUploadInitFile() {
        String cacheKey = "default";

        String cachedId = initFileIdCache.get(cacheKey);
        if (cachedId != null) {
            log.debug("Using cached init file ID '{}'", cachedId);
            return cachedId;
        }

        log.info("Uploading shared init file for dataset creation");
        String fileId = uploadInitFile();
        initFileIdCache.put(cacheKey, fileId);
        return fileId;
    }

    /**
     * Upload a small placeholder text file to satisfy the API requirement
     * that file_ids must not be empty when creating a dataset.
     * Always uses the default token since the init file is shared across all users.
     *
     * @return the uploaded file ID
     */
    private String uploadInitFile() {
        String url = properties.getBaseUrl() + properties.getFileUploadPath() + "?source=datasets";
        String fileName = properties.getInitFileName();
        byte[] content = properties.getInitFileContent().getBytes(StandardCharsets.UTF_8);

        RequestBody fileBody = RequestBody.create(content, MediaType.parse("text/plain"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();

        try {
            String responseStr = executeMultipartWithRetry(url, requestBody, null, null);
            FileUploadResponse uploadResponse = objectMapper.readValue(responseStr, FileUploadResponse.class);
            String fileId = uploadResponse.getId();
            log.info("Init file uploaded successfully: id={}, name={}", fileId, fileName);
            return fileId;
        } catch (Exception e) {
            throw new KnowledgeException("Failed to upload init file for dataset creation", e);
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file, String username, String email) {
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new KnowledgeException("File size " + file.getSize()
                    + " exceeds maximum allowed size " + properties.getMaxFileSize());
        }

        String url = properties.getBaseUrl() + properties.getFileUploadPath() + "?source=datasets";

        try {
            RequestBody fileBody = RequestBody.create(
                    file.getBytes(),
                    MediaType.parse(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            );

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getOriginalFilename(), fileBody)
                    .build();

            String responseStr = executeMultipartWithRetry(url, requestBody, username, email);
            return objectMapper.readValue(responseStr, FileUploadResponse.class);
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeException("Failed to upload file: " + file.getOriginalFilename(), e);
        }
    }

    public List<FileUploadResponse> uploadFiles(List<MultipartFile> files, String username, String email) {
        List<FileUploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadFile(file, username, email));
        }
        return results;
    }

    public String createDocumentInDataset(String datasetId, List<String> fileIds, String username, String email) {
        String url = properties.getBaseUrl() + properties.getDatasetDocumentsPath()
                .replace("{datasetId}", datasetId);
        String body = buildDocumentCreateBody(fileIds);

        String responseBody = executeWithRetry("POST", url, body, username, email);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("batch")) {
                return root.get("batch").asText();
            }
            return responseBody;
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse create document response", e);
        }
    }

    public String initDatasetWithDocuments(String datasetName, List<String> fileIds, String username, String email) {
        String url = properties.getBaseUrl() + properties.getDatasetInitPath();
        String body = buildInitDatasetBody(datasetName, fileIds);

        String responseBody = executeWithRetry("POST", url, body, username, email);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("dataset") && root.get("dataset").has("id")) {
                return root.get("dataset").get("id").asText();
            }
            if (root.has("id")) {
                return root.get("id").asText();
            }
            return responseBody;
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse init dataset response", e);
        }
    }

    public DatasetListResponse listDatasets(String keyword, int page, int limit, String username, String email) {
        String url = properties.getBaseUrl() + properties.getDatasetListPath() + "?page=" + page
                + "&limit=" + limit;
        if (keyword != null && !keyword.isEmpty()) {
            try {
                url += "&keyword=" + URLEncoder.encode(keyword, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new KnowledgeException("UTF-8 encoding not supported", e);
            }
        }

        String responseBody = executeWithRetry("GET", url, null, username, email);
        try {
            return objectMapper.readValue(responseBody, DatasetListResponse.class);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse dataset list response", e);
        }
    }

    public void deleteDocument(String datasetId, String documentId, String username, String email) {
        String url = properties.getBaseUrl() + properties.getDatasetDocumentByIdPath()
                .replace("{datasetId}", datasetId)
                .replace("{documentId}", documentId);
        executeWithRetry("DELETE", url, null, username, email);
    }

    public void deleteDataset(String datasetId, String username, String email) {
        String url = properties.getBaseUrl() + properties.getDatasetByIdPath()
                .replace("{datasetId}", datasetId);
        executeWithRetry("DELETE", url, null, username, email);
    }

    // ===== Internal execution with token resolution =====

    private String executeWithRetry(String method, String url, String body, String username, String email) {
        try {
            return doExecute(method, url, body, false, username, email);
        } catch (KnowledgeException e) {
            if (e.getStatusCode() == 401) {
                if (username != null && email != null) {
                    log.info("Received 401, refreshing token for user '{}' and retrying: {}", username, url);
                    tokenManager.invalidateTokenForUser(username, email);
                } else {
                    log.info("Received 401, refreshing default user token and retrying: {}", url);
                    tokenManager.invalidateToken();
                }
                return doExecute(method, url, body, true, username, email);
            }
            throw e;
        }
    }

    private String executeMultipartWithRetry(String url, RequestBody requestBody, String username, String email) {
        try {
            return doExecuteMultipart(url, requestBody, false, username, email);
        } catch (KnowledgeException e) {
            if (e.getStatusCode() == 401) {
                if (username != null && email != null) {
                    log.info("Received 401, refreshing token for user '{}' and retrying multipart: {}", username, url);
                    tokenManager.invalidateTokenForUser(username, email);
                } else {
                    log.info("Received 401, refreshing default user token and retrying multipart: {}", url);
                    tokenManager.invalidateToken();
                }
                return doExecuteMultipart(url, requestBody, true, username, email);
            }
            throw e;
        }
    }

    private String resolveToken(String username, String email) {
        if (username != null && email != null) {
            log.debug("Resolving token for specified user: {}", username);
            return tokenManager.getTokenForUser(username, email);
        }
        if (username != null || email != null) {
            log.warn("Only one of username/email provided (username={}, email={}), "
                    + "both are required for per-user token. Falling back to default token. "
                    + "Check that the calling method passes both username AND email.", username, email);
        }
        log.debug("Resolving token for default user");
        return tokenManager.getToken();
    }

    private String doExecute(String method, String url, String body, boolean isRetry, String username, String email) {
        String token = resolveToken(username, email);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token);

        switch (method.toUpperCase()) {
            case "POST":
                requestBuilder.post(RequestBody.create(
                        body != null ? body : "", JSON_MEDIA_TYPE));
                break;
            case "DELETE":
                requestBuilder.delete();
                break;
            case "PATCH":
                requestBuilder.patch(RequestBody.create(
                        body != null ? body : "", JSON_MEDIA_TYPE));
                break;
            case "GET":
            default:
                requestBuilder.get();
                break;
        }

        Request request = requestBuilder.build();
        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("HTTP {} request: url={}, time={}ms, status={}",
                    method, url, elapsed, response.code());

            String responseBody = response.body() != null ? response.body().string() : "";

            if (response.code() == 401 && !isRetry) {
                throw new KnowledgeException("Unauthorized", 401);
            }

            if (!response.isSuccessful() && response.code() != 204) {
                log.error("HTTP {} failed: url={}, status={}, body={}",
                        method, url, response.code(), responseBody);
                throw new KnowledgeException(
                        "HTTP " + method + " failed: " + url + " status=" + response.code(),
                        response.code());
            }

            return responseBody;
        } catch (KnowledgeException e) {
            throw e;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("HTTP {} error: url={}, time={}ms, error={}",
                    method, url, elapsed, e.getMessage());
            throw new KnowledgeException("HTTP " + method + " failed: " + url, e);
        }
    }

    private String doExecuteMultipart(String url, RequestBody requestBody, boolean isRetry, String username, String email) {
        String token = resolveToken(username, email);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .post(requestBody)
                .build();

        long startTime = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("HTTP multipart upload: url={}, time={}ms, status={}",
                    url, elapsed, response.code());

            String responseStr = response.body() != null ? response.body().string() : "";

            if (response.code() == 401 && !isRetry) {
                throw new KnowledgeException("Unauthorized", 401);
            }

            if (!response.isSuccessful()) {
                log.error("File upload failed: url={}, status={}, body={}",
                        url, response.code(), responseStr);
                throw new KnowledgeException(
                        "File upload failed: " + url + " status=" + response.code(),
                        response.code());
            }

            return responseStr;
        } catch (KnowledgeException e) {
            throw e;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("File upload error: url={}, time={}ms, error={}",
                    url, elapsed, e.getMessage());
            throw new KnowledgeException("File upload failed: " + url, e);
        }
    }

    private String buildDocumentCreateBody(List<String> fileIds) {
        return "{" + buildDataSourceAndConfig(fileIds) + "}";
    }

    private String buildInitDatasetBody(String datasetName, List<String> fileIds) {
        return "{"
                + "\"name\":\"" + escapeJson(datasetName) + "\","
                + buildDataSourceAndConfig(fileIds)
                + "}";
    }

    private String buildDataSourceAndConfig(List<String> fileIds) {
        StringBuilder fileIdsJson = new StringBuilder("[");
        for (int i = 0; i < fileIds.size(); i++) {
            if (i > 0) fileIdsJson.append(",");
            fileIdsJson.append("\"").append(escapeJson(fileIds.get(i))).append("\"");
        }
        fileIdsJson.append("]");

        return "\"data_source\":{\"type\":\"upload_file\","
                + "\"info_list\":{\"data_source_type\":\"upload_file\","
                + "\"file_info_list\":{\"file_ids\":" + fileIdsJson + "}}},"
                + buildConfigFields();
    }

    private String buildConfigFields() {
        return "\"indexing_technique\":\"" + escapeJson(properties.getIndexingTechnique()) + "\","
                + "\"process_rule\":{\"rules\":{"
                + "\"pre_processing_rules\":["
                + "{\"id\":\"remove_extra_spaces\",\"enabled\":true},"
                + "{\"id\":\"remove_urls_emails\",\"enabled\":false}],"
                + "\"segmentation\":{\"separator\":\"" + escapeJson(properties.getSegmentSeparator()) + "\","
                + "\"max_tokens\":" + properties.getSegmentMaxTokens() + ","
                + "\"chunk_overlap\":" + properties.getSegmentChunkOverlap() + "}},"
                + "\"mode\":\"" + escapeJson(properties.getProcessRuleMode()) + "\"},"
                + "\"doc_form\":\"" + escapeJson(properties.getDocForm()) + "\","
                + "\"doc_language\":\"" + escapeJson(properties.getDocLanguage()) + "\","
                + "\"retrieval_model\":{\"search_method\":\"" + escapeJson(properties.getSearchMethod()) + "\","
                + "\"reranking_enable\":" + properties.isRerankingEnable() + ","
                + "\"reranking_model\":{\"reranking_provider_name\":\"" + escapeJson(properties.getRerankingProviderName()) + "\","
                + "\"reranking_model_name\":\"" + escapeJson(properties.getRerankingModelName()) + "\"},"
                + "\"top_k\":" + properties.getTopK() + ","
                + "\"score_threshold_enabled\":" + properties.isScoreThresholdEnabled() + ","
                + "\"score_threshold\":" + properties.getScoreThreshold() + ","
                + "\"reranking_mode\":\"" + escapeJson(properties.getRerankingMode()) + "\","
                + "\"weights\":{\"weight_type\":\"" + escapeJson(properties.getWeightType()) + "\","
                + "\"vector_setting\":{\"vector_weight\":" + properties.getVectorWeight() + ","
                + "\"embedding_provider_name\":\"\",\"embedding_model_name\":\"\"},"
                + "\"keyword_setting\":{\"keyword_weight\":" + properties.getKeywordWeight() + "}}},"
                + "\"embedding_model\":\"" + escapeJson(properties.getEmbeddingModel()) + "\","
                + "\"embedding_model_provider\":\"" + escapeJson(properties.getEmbeddingModelProvider()) + "\"";
    }

    private String escapeJson(String value) {
        return JsonUtil.escapeJson(value);
    }
}
