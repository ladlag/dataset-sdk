package com.knowledge.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.model.*;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHttpClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final KnowledgeProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;

    public KnowledgeHttpClient(KnowledgeProperties properties, TokenManager tokenManager,
                               ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public DatasetResponse createDataset(String name) {
        String url = properties.getBaseUrl() + "/console/api/datasets/init";
        String body = "{\"name\":\"" + escapeJson(name) + "\"}";

        String responseBody = executeWithRetry("POST", url, body);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode datasetNode = root.has("dataset") ? root.get("dataset") : root;
            return objectMapper.treeToValue(datasetNode, DatasetResponse.class);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse create dataset response", e);
        }
    }

    public FileUploadResponse uploadFile(MultipartFile file) {
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new KnowledgeException("File size " + file.getSize()
                    + " exceeds maximum allowed size " + properties.getMaxFileSize());
        }

        String url = properties.getBaseUrl() + "/console/api/files/upload?source=datasets";

        try {
            RequestBody fileBody = RequestBody.create(
                    file.getBytes(),
                    MediaType.parse(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
            );

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getOriginalFilename(), fileBody)
                    .build();

            String responseStr = executeMultipartWithRetry(url, requestBody);
            return objectMapper.readValue(responseStr, FileUploadResponse.class);
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeException("Failed to upload file: " + file.getOriginalFilename(), e);
        }
    }

    public List<FileUploadResponse> uploadFiles(List<MultipartFile> files) {
        List<FileUploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadFile(file));
        }
        return results;
    }

    public String createDocumentInDataset(String datasetId, List<String> fileIds) {
        String url = properties.getBaseUrl() + "/console/api/datasets/" + datasetId + "/documents";
        String body = buildDocumentCreateBody(fileIds);

        String responseBody = executeWithRetry("POST", url, body);
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

    public String initDatasetWithDocuments(String datasetName, List<String> fileIds) {
        String url = properties.getBaseUrl() + "/console/api/datasets/init";
        String body = buildInitDatasetBody(datasetName, fileIds);

        String responseBody = executeWithRetry("POST", url, body);
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

    public DatasetListResponse listDatasets(String keyword, int page, int limit) {
        String url = properties.getBaseUrl() + "/console/api/datasets?page=" + page
                + "&limit=" + limit;
        if (keyword != null && !keyword.isEmpty()) {
            url += "&keyword=" + keyword;
        }

        String responseBody = executeWithRetry("GET", url, null);
        try {
            return objectMapper.readValue(responseBody, DatasetListResponse.class);
        } catch (Exception e) {
            throw new KnowledgeException("Failed to parse dataset list response", e);
        }
    }

    public void deleteDocument(String datasetId, String documentId) {
        String url = properties.getBaseUrl() + "/console/api/datasets/" + datasetId
                + "/documents/" + documentId;
        executeWithRetry("DELETE", url, null);
    }

    public void deleteDataset(String datasetId) {
        String url = properties.getBaseUrl() + "/console/api/datasets/" + datasetId;
        executeWithRetry("DELETE", url, null);
    }

    private String executeWithRetry(String method, String url, String body) {
        try {
            return doExecute(method, url, body, false);
        } catch (KnowledgeException e) {
            if (e.getStatusCode() == 401) {
                log.info("Received 401, refreshing token and retrying: {}", url);
                tokenManager.invalidateToken();
                return doExecute(method, url, body, true);
            }
            throw e;
        }
    }

    private String executeMultipartWithRetry(String url, RequestBody requestBody) {
        try {
            return doExecuteMultipart(url, requestBody, false);
        } catch (KnowledgeException e) {
            if (e.getStatusCode() == 401) {
                log.info("Received 401, refreshing token and retrying multipart: {}", url);
                tokenManager.invalidateToken();
                return doExecuteMultipart(url, requestBody, true);
            }
            throw e;
        }
    }

    private String doExecute(String method, String url, String body, boolean isRetry) {
        String token = tokenManager.getToken();

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

    private String doExecuteMultipart(String url, RequestBody requestBody, boolean isRetry) {
        String token = tokenManager.getToken();

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
        StringBuilder fileIdsJson = new StringBuilder("[");
        for (int i = 0; i < fileIds.size(); i++) {
            if (i > 0) fileIdsJson.append(",");
            fileIdsJson.append("\"").append(escapeJson(fileIds.get(i))).append("\"");
        }
        fileIdsJson.append("]");

        return "{"
                + "\"data_source\":{\"type\":\"upload_file\","
                + "\"info_list\":{\"data_source_type\":\"upload_file\","
                + "\"file_info_list\":{\"file_ids\":" + fileIdsJson + "}}},"
                + "\"indexing_technique\":\"high_quality\","
                + "\"process_rule\":{\"rules\":{"
                + "\"pre_processing_rules\":["
                + "{\"id\":\"remove_extra_spaces\",\"enabled\":true},"
                + "{\"id\":\"remove_urls_emails\",\"enabled\":false}],"
                + "\"segmentation\":{\"separator\":\"\\n\\n\",\"max_tokens\":500,\"chunk_overlap\":50}},"
                + "\"mode\":\"custom\"},"
                + "\"doc_form\":\"text_model\","
                + "\"doc_language\":\"English\","
                + "\"retrieval_model\":{\"search_method\":\"hybrid_search\","
                + "\"reranking_enable\":true,"
                + "\"reranking_model\":{\"reranking_provider_name\":\"langgenius/tongyi/tongyi\","
                + "\"reranking_model_name\":\"gte-rerank-v2\"},"
                + "\"top_k\":3,\"score_threshold_enabled\":false,\"score_threshold\":0.5,"
                + "\"reranking_mode\":\"reranking_model\","
                + "\"weights\":{\"weight_type\":\"customized\","
                + "\"vector_setting\":{\"vector_weight\":0.7,"
                + "\"embedding_provider_name\":\"\",\"embedding_model_name\":\"\"},"
                + "\"keyword_setting\":{\"keyword_weight\":0.3}}},"
                + "\"embedding_model\":\"text-embedding-v2\","
                + "\"embedding_model_provider\":\"langgenius/tongyi/tongyi\""
                + "}";
    }

    private String buildInitDatasetBody(String datasetName, List<String> fileIds) {
        StringBuilder fileIdsJson = new StringBuilder("[");
        for (int i = 0; i < fileIds.size(); i++) {
            if (i > 0) fileIdsJson.append(",");
            fileIdsJson.append("\"").append(escapeJson(fileIds.get(i))).append("\"");
        }
        fileIdsJson.append("]");

        return "{"
                + "\"name\":\"" + escapeJson(datasetName) + "\","
                + "\"data_source\":{\"type\":\"upload_file\","
                + "\"info_list\":{\"data_source_type\":\"upload_file\","
                + "\"file_info_list\":{\"file_ids\":" + fileIdsJson + "}}},"
                + "\"indexing_technique\":\"high_quality\","
                + "\"process_rule\":{\"rules\":{"
                + "\"pre_processing_rules\":["
                + "{\"id\":\"remove_extra_spaces\",\"enabled\":true},"
                + "{\"id\":\"remove_urls_emails\",\"enabled\":false}],"
                + "\"segmentation\":{\"separator\":\"\\n\\n\",\"max_tokens\":500,\"chunk_overlap\":50}},"
                + "\"mode\":\"custom\"},"
                + "\"doc_form\":\"text_model\","
                + "\"doc_language\":\"English\","
                + "\"retrieval_model\":{\"search_method\":\"hybrid_search\","
                + "\"reranking_enable\":true,"
                + "\"reranking_model\":{\"reranking_provider_name\":\"langgenius/tongyi/tongyi\","
                + "\"reranking_model_name\":\"gte-rerank-v2\"},"
                + "\"top_k\":3,\"score_threshold_enabled\":false,\"score_threshold\":0.5,"
                + "\"reranking_mode\":\"reranking_model\","
                + "\"weights\":{\"weight_type\":\"customized\","
                + "\"vector_setting\":{\"vector_weight\":0.7,"
                + "\"embedding_provider_name\":\"\",\"embedding_model_name\":\"\"},"
                + "\"keyword_setting\":{\"keyword_weight\":0.3}}},"
                + "\"embedding_model\":\"text-embedding-v2\","
                + "\"embedding_model_provider\":\"langgenius/tongyi/tongyi\""
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
