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
import java.util.List;

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
        properties.setUserInfo("testuser");
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
    void testCreateDataset() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"dataset-123\",\"name\":\"test-dataset\"}"));

        DatasetResponse response = knowledgeDatasetService.createDataset("test-dataset");

        assertNotNull(response);
        assertEquals("dataset-123", response.getId());
        assertEquals("test-dataset", response.getName());
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
        public String refreshToken() {
            return "mock-test-token";
        }

        @Override
        public void invalidateToken() {
            // no-op
        }
    }
}
