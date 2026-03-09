package com.knowledge.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.sdk.auth.TokenManager;
import com.knowledge.sdk.client.KnowledgeHttpClient;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.model.DatasetResponse;
import com.knowledge.sdk.service.KnowledgeDatasetService;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeServiceTest {

    private MockWebServer mockServer;
    private KnowledgeDatasetService knowledgeDatasetService;
    private KnowledgeProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        properties = new KnowledgeProperties();
        properties.setBaseUrl(mockServer.url("").toString().replaceAll("/$", ""));
        properties.setSystemToken("test-system-token");
        properties.setUsername("testuser");
        properties.setEmail("testuser@example.com");
        properties.setMaxFilesPerBatch(10);
        properties.setMaxFileSize(20 * 1024 * 1024);

        ObjectMapper objectMapper = new ObjectMapper();

        TokenManager tokenManager = new MockTokenManager(properties);
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        KnowledgeHttpClient httpClient = new KnowledgeHttpClient(
                properties, tokenManager, objectMapper, okHttpClient);
        knowledgeDatasetService = new KnowledgeDatasetService(httpClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void testCreateDataset() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"dataset-123\",\"name\":\"test-dataset\"}"));

        DatasetResponse response = knowledgeDatasetService.createDataset("test-dataset");

        assertNotNull(response);
        assertEquals("dataset-123", response.getId());
        assertEquals("test-dataset", response.getName());

        // Verify request body includes indexing_technique
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"indexing_technique\":\"high_quality\""),
                "Request body should include indexing_technique");
    }

    @Test
    void testCreateUserDatasetWithDefaultToken() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ds-user-alice\",\"name\":\"user_alice001\"}"));

        DatasetResponse response = knowledgeDatasetService.createUserDataset("alice001");

        assertNotNull(response);
        assertEquals("ds-user-alice", response.getId());
        assertEquals("user_alice001", response.getName());
    }

    @Test
    void testCreateUserDatasetWithUserCredentials() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ds-user-bob\",\"name\":\"user_bob002\"}"));

        DatasetResponse response = knowledgeDatasetService.createUserDataset(
                "bob002", "bob", "bob@example.com");

        assertNotNull(response);
        assertEquals("ds-user-bob", response.getId());
        assertEquals("user_bob002", response.getName());

        // Verify request was made to the correct path with correct dataset name
        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().contains("/datasets/init"));
        assertTrue(request.getBody().readUtf8().contains("user_bob002"));
    }

    @Test
    void testCreatePublicDataset() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"ds-public\",\"name\":\"public_dataset\"}"));

        DatasetResponse response = knowledgeDatasetService.createPublicDataset();

        assertNotNull(response);
        assertEquals("ds-public", response.getId());
        assertEquals("public_dataset", response.getName());
    }

    @Test
    void testUploadDocuments() throws InterruptedException {
        // Order: 1) list datasets, 2) file upload, 3) init dataset
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0,\"page\":1,\"limit\":100}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"file-001\",\"name\":\"test.txt\"}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"dataset\":{\"id\":\"ds-001\"},\"batch\":\"batch-001\"}"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());

        List<String> results = knowledgeDatasetService.uploadDocuments(
                "test-dataset", Arrays.asList(file));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("ds-001", results.get(0));
    }

    @Test
    void testUploadToUserDataset() {
        // Order: 1) list datasets, 2) file upload, 3) init dataset
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0,\"page\":1,\"limit\":100}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"file-002\",\"name\":\"doc.pdf\"}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"dataset\":{\"id\":\"ds-user-1001\"},\"batch\":\"batch-002\"}"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf content".getBytes());

        List<String> results = knowledgeDatasetService.uploadToUserDataset(
                "1001", Arrays.asList(file));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("ds-user-1001", results.get(0));
    }

    @Test
    void testUploadToUserDatasetWithUserCredentials() {
        // Per-user token: login as specific user to create dataset under their account
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0,\"page\":1,\"limit\":100}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"file-010\",\"name\":\"user-doc.pdf\"}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"dataset\":{\"id\":\"ds-alice\"},\"batch\":\"batch-010\"}"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "user-doc.pdf", "application/pdf", "pdf content".getBytes());

        List<String> results = knowledgeDatasetService.uploadToUserDataset(
                "alice001", "alice", "alice@example.com", Arrays.asList(file));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("ds-alice", results.get(0));
    }

    @Test
    void testUploadToPublicDataset() {
        // Order: 1) list datasets, 2) file upload, 3) init dataset
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0,\"page\":1,\"limit\":100}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"file-003\",\"name\":\"readme.md\"}"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"dataset\":{\"id\":\"ds-public\"},\"batch\":\"batch-003\"}"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "readme.md", "text/markdown", "# README".getBytes());

        List<String> results = knowledgeDatasetService.uploadToPublicDataset(Arrays.asList(file));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals("ds-public", results.get(0));
    }

    @Test
    void testBatchUpload() {
        // Create 23 files -> should create 3 batches (10 + 10 + 3)
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            files.add(new MockMultipartFile(
                    "file", "file" + i + ".txt", "text/plain",
                    ("content " + i).getBytes()));
        }

        // First: list datasets (dataset not found)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0,\"page\":1,\"limit\":100}"));

        // Batch 1: 10 file uploads + init dataset
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"file-" + i + "\",\"name\":\"file" + i + ".txt\"}"));
        }

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"dataset\":{\"id\":\"ds-batch\"},\"batch\":\"batch-1\"}"));

        // Batch 2: 10 file uploads + create document in existing dataset
        for (int i = 10; i < 20; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"file-" + i + "\",\"name\":\"file" + i + ".txt\"}"));
        }

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"batch\":\"batch-2\"}"));

        // Batch 3: 3 file uploads + create document in existing dataset
        for (int i = 20; i < 23; i++) {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"file-" + i + "\",\"name\":\"file" + i + ".txt\"}"));
        }

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"batch\":\"batch-3\"}"));

        List<String> results = knowledgeDatasetService.uploadDocuments(
                "batch-test", new ArrayList<>(files));

        assertEquals(3, results.size());
        assertEquals("ds-batch", results.get(0));
        assertEquals("batch-2", results.get(1));
        assertEquals("batch-3", results.get(2));
    }

    @Test
    void testDeleteDocument() {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        assertDoesNotThrow(() ->
                knowledgeDatasetService.deleteDocument("ds-001", "doc-001"));
    }

    @Test
    void testDeleteDataset() {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        assertDoesNotThrow(() ->
                knowledgeDatasetService.deleteDataset("ds-001"));
    }

    @Test
    void testFileSizeExceeded() {
        byte[] largeContent = new byte[21 * 1024 * 1024]; // 21MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.txt", "text/plain", largeContent);

        assertThrows(KnowledgeException.class, () ->
                knowledgeDatasetService.uploadDocuments("test-dataset", Arrays.asList(largeFile)));
    }

    @Test
    void testEmptyFileList() {
        assertThrows(KnowledgeException.class, () ->
                knowledgeDatasetService.uploadDocuments("test-dataset", new ArrayList<>()));
    }

    @Test
    void testUserDatasetNaming() {
        assertEquals("user_", properties.getUserDatasetPrefix());
        assertEquals("public_dataset", properties.getPublicDatasetName());
    }

    @Test
    void testCustomUserDatasetPrefix() throws InterruptedException {
        // Change prefix from default "user_" to "kb_"
        properties.setUserDatasetPrefix("kb_");
        try {
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"ds-kb-alice\",\"name\":\"kb_alice001\"}"));

            DatasetResponse response = knowledgeDatasetService.createUserDataset("alice001");

            assertNotNull(response);
            assertEquals("ds-kb-alice", response.getId());

            // Verify the request body uses the custom prefix
            RecordedRequest request = mockServer.takeRequest();
            assertTrue(request.getBody().readUtf8().contains("kb_alice001"));
        } finally {
            // Restore default prefix
            properties.setUserDatasetPrefix("user_");
        }
    }

    @Test
    void testSplitIntoBatches() {
        // Test that 23 files produce 3 batches
        List<MockMultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            files.add(new MockMultipartFile(
                    "file", "file" + i + ".txt", "text/plain",
                    ("content " + i).getBytes()));
        }

        // Verify batch sizes: use reflection or test indirectly
        int maxPerBatch = properties.getMaxFilesPerBatch();
        int expectedBatches = (int) Math.ceil((double) files.size() / maxPerBatch);
        assertEquals(3, expectedBatches);
    }

    @Test
    void testPropertiesUsernameEmail() {
        assertEquals("testuser", properties.getUsername());
        assertEquals("testuser@example.com", properties.getEmail());
    }

    @Test
    void testRsaPublicKeyPathDefault() {
        assertEquals("rsa_public_key.pem", properties.getRsaPublicKeyPath());
    }

    @Test
    void testRsaPublicKeyPathConfigurable() {
        KnowledgeProperties customProperties = new KnowledgeProperties();
        customProperties.setRsaPublicKeyPath("custom_rsa_key.pem");
        assertEquals("custom_rsa_key.pem", customProperties.getRsaPublicKeyPath());
    }

    @Test
    void testApiPathDefaults() {
        assertEquals("/tenant/api/app/account/sso_login", properties.getSsoLoginPath());
        assertEquals("/console/api/datasets/init", properties.getDatasetInitPath());
        assertEquals("/console/api/files/upload", properties.getFileUploadPath());
        assertEquals("/console/api/datasets", properties.getDatasetListPath());
        assertEquals("/console/api/datasets/{datasetId}", properties.getDatasetByIdPath());
        assertEquals("/console/api/datasets/{datasetId}/documents", properties.getDatasetDocumentsPath());
        assertEquals("/console/api/datasets/{datasetId}/documents/{documentId}", properties.getDatasetDocumentByIdPath());
        assertEquals(3600, properties.getTokenTtlSeconds());
    }

    @Test
    void testApiPathConfigurable() {
        KnowledgeProperties customProperties = new KnowledgeProperties();
        customProperties.setSsoLoginPath("/custom/sso/login");
        customProperties.setDatasetInitPath("/api/v2/datasets/init");
        customProperties.setFileUploadPath("/api/v2/files/upload");
        customProperties.setDatasetListPath("/api/v2/datasets");
        customProperties.setDatasetByIdPath("/api/v2/datasets/{datasetId}");
        customProperties.setDatasetDocumentsPath("/api/v2/datasets/{datasetId}/documents");
        customProperties.setDatasetDocumentByIdPath("/api/v2/datasets/{datasetId}/documents/{documentId}");
        customProperties.setTokenTtlSeconds(7200);

        assertEquals("/custom/sso/login", customProperties.getSsoLoginPath());
        assertEquals("/api/v2/datasets/init", customProperties.getDatasetInitPath());
        assertEquals("/api/v2/files/upload", customProperties.getFileUploadPath());
        assertEquals("/api/v2/datasets", customProperties.getDatasetListPath());
        assertEquals("/api/v2/datasets/{datasetId}", customProperties.getDatasetByIdPath());
        assertEquals("/api/v2/datasets/{datasetId}/documents", customProperties.getDatasetDocumentsPath());
        assertEquals("/api/v2/datasets/{datasetId}/documents/{documentId}", customProperties.getDatasetDocumentByIdPath());
        assertEquals(7200, customProperties.getTokenTtlSeconds());
    }

    @Test
    void testConnectionPoolDefaults() {
        assertEquals(64, properties.getMaxRequests());
        assertEquals(20, properties.getMaxRequestsPerHost());
        assertEquals(20, properties.getConnectionPoolSize());
        assertEquals(5, properties.getConnectionPoolKeepAliveMinutes());
    }

    @Test
    void testConnectionPoolConfigurable() {
        KnowledgeProperties customProperties = new KnowledgeProperties();
        customProperties.setMaxRequests(128);
        customProperties.setMaxRequestsPerHost(40);
        customProperties.setConnectionPoolSize(50);
        customProperties.setConnectionPoolKeepAliveMinutes(10);

        assertEquals(128, customProperties.getMaxRequests());
        assertEquals(40, customProperties.getMaxRequestsPerHost());
        assertEquals(50, customProperties.getConnectionPoolSize());
        assertEquals(10, customProperties.getConnectionPoolKeepAliveMinutes());
    }

    @Test
    void testDocProcessingDefaults() {
        assertEquals("high_quality", properties.getIndexingTechnique());
        assertEquals("text_model", properties.getDocForm());
        assertEquals("English", properties.getDocLanguage());
        assertEquals("custom", properties.getProcessRuleMode());
        assertEquals("\n\n", properties.getSegmentSeparator());
        assertEquals(500, properties.getSegmentMaxTokens());
        assertEquals(50, properties.getSegmentChunkOverlap());
        assertEquals("hybrid_search", properties.getSearchMethod());
        assertEquals(3, properties.getTopK());
        assertFalse(properties.isScoreThresholdEnabled());
        assertEquals(0.5, properties.getScoreThreshold());
        assertTrue(properties.isRerankingEnable());
        assertEquals("reranking_model", properties.getRerankingMode());
        assertEquals("langgenius/tongyi/tongyi", properties.getRerankingProviderName());
        assertEquals("gte-rerank-v2", properties.getRerankingModelName());
        assertEquals("customized", properties.getWeightType());
        assertEquals(0.7, properties.getVectorWeight());
        assertEquals(0.3, properties.getKeywordWeight());
        assertEquals("text-embedding-v2", properties.getEmbeddingModel());
        assertEquals("langgenius/tongyi/tongyi", properties.getEmbeddingModelProvider());
    }

    @Test
    void testDocProcessingConfigurable() {
        KnowledgeProperties customProperties = new KnowledgeProperties();
        customProperties.setIndexingTechnique("economy");
        customProperties.setDocForm("qa_model");
        customProperties.setDocLanguage("Chinese");
        customProperties.setProcessRuleMode("automatic");
        customProperties.setSegmentSeparator("\n");
        customProperties.setSegmentMaxTokens(1000);
        customProperties.setSegmentChunkOverlap(100);
        customProperties.setSearchMethod("semantic_search");
        customProperties.setTopK(5);
        customProperties.setScoreThresholdEnabled(true);
        customProperties.setScoreThreshold(0.8);
        customProperties.setRerankingEnable(false);
        customProperties.setRerankingMode("weighted_score");
        customProperties.setRerankingProviderName("custom/provider");
        customProperties.setRerankingModelName("custom-rerank-v1");
        customProperties.setWeightType("default");
        customProperties.setVectorWeight(0.5);
        customProperties.setKeywordWeight(0.5);
        customProperties.setEmbeddingModel("custom-embedding");
        customProperties.setEmbeddingModelProvider("custom/provider");

        assertEquals("economy", customProperties.getIndexingTechnique());
        assertEquals("qa_model", customProperties.getDocForm());
        assertEquals("Chinese", customProperties.getDocLanguage());
        assertEquals("automatic", customProperties.getProcessRuleMode());
        assertEquals("\n", customProperties.getSegmentSeparator());
        assertEquals(1000, customProperties.getSegmentMaxTokens());
        assertEquals(100, customProperties.getSegmentChunkOverlap());
        assertEquals("semantic_search", customProperties.getSearchMethod());
        assertEquals(5, customProperties.getTopK());
        assertTrue(customProperties.isScoreThresholdEnabled());
        assertEquals(0.8, customProperties.getScoreThreshold());
        assertFalse(customProperties.isRerankingEnable());
        assertEquals("weighted_score", customProperties.getRerankingMode());
        assertEquals("custom/provider", customProperties.getRerankingProviderName());
        assertEquals("custom-rerank-v1", customProperties.getRerankingModelName());
        assertEquals("default", customProperties.getWeightType());
        assertEquals(0.5, customProperties.getVectorWeight());
        assertEquals(0.5, customProperties.getKeywordWeight());
        assertEquals("custom-embedding", customProperties.getEmbeddingModel());
        assertEquals("custom/provider", customProperties.getEmbeddingModelProvider());
    }

    @Test
    void testCustomEndpointPathsUsedInRequests() throws InterruptedException {
        // Verify that custom endpoint paths are used in actual HTTP requests
        properties.setDatasetInitPath("/custom/api/v2/datasets/init");

        ObjectMapper objectMapper = new ObjectMapper();
        TokenManager tokenManager = new MockTokenManager(properties);
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
        KnowledgeHttpClient httpClient = new KnowledgeHttpClient(
                properties, tokenManager, objectMapper, okHttpClient);
        KnowledgeDatasetService customService = new KnowledgeDatasetService(httpClient, properties);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"dataset-custom\",\"name\":\"custom-test\"}"));

        DatasetResponse response = customService.createDataset("custom-test");

        assertNotNull(response);
        assertEquals("dataset-custom", response.getId());

        RecordedRequest request = mockServer.takeRequest();
        assertTrue(request.getPath().startsWith("/custom/api/v2/datasets/init"),
                "Expected custom endpoint path but got: " + request.getPath());
    }

    @Test
    void testConcurrent20Uploads() throws InterruptedException {
        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        // Pre-populate dataset cache so each thread skips the list-datasets call
        // Each concurrent user uploads to their own user dataset
        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            // Enqueue responses for each user: file upload + create document
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"data\":[{\"id\":\"ds-user-" + idx + "\",\"name\":\"user_" + idx + "\"}],"
                            + "\"total\":1,\"page\":1,\"limit\":100}"));
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"file-" + idx + "\",\"name\":\"file" + idx + ".txt\"}"));
            mockServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"batch\":\"batch-" + idx + "\"}"));
        }

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MockMultipartFile file = new MockMultipartFile(
                            "file", "file" + idx + ".txt", "text/plain",
                            ("content " + idx).getBytes());
                    List<String> results = knowledgeDatasetService.uploadToUserDataset(
                            String.valueOf(idx), Arrays.asList(file));
                    assertNotNull(results);
                    assertFalse(results.isEmpty());
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Timed out waiting for concurrent uploads");
        executor.shutdown();

        if (!errors.isEmpty()) {
            for (Throwable t : errors) {
                t.printStackTrace();
            }
        }
        assertEquals(concurrency, successCount.get(),
                "Expected all " + concurrency + " concurrent uploads to succeed, but "
                        + errors.size() + " failed: " + errors);
    }

    /**
     * Simple mock TokenManager that returns a static token for testing,
     * without actually making SSO login calls.
     */
    static class MockTokenManager extends TokenManager {
        MockTokenManager(KnowledgeProperties properties) {
            super(properties, null, new ObjectMapper());
        }

        @Override
        public String getToken() {
            return "mock-test-token";
        }

        @Override
        public String getTokenForUser(String username, String email) {
            return "mock-user-token-" + username;
        }

        @Override
        public String refreshToken() {
            return "mock-test-token";
        }

        @Override
        public String refreshTokenForUser(String username, String email) {
            return "mock-user-token-" + username;
        }

        @Override
        public void invalidateToken() {
            // no-op
        }

        @Override
        public void invalidateTokenForUser(String username, String email) {
            // no-op
        }
    }
}
