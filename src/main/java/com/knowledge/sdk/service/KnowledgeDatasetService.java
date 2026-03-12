package com.knowledge.sdk.service;

import com.knowledge.sdk.client.KnowledgeHttpClient;
import com.knowledge.sdk.config.KnowledgeProperties;
import com.knowledge.sdk.exception.KnowledgeException;
import com.knowledge.sdk.model.DatasetListResponse;
import com.knowledge.sdk.model.DatasetResponse;
import com.knowledge.sdk.model.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeDatasetService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDatasetService.class);

    private final KnowledgeHttpClient httpClient;
    private final KnowledgeProperties properties;

    public KnowledgeDatasetService(KnowledgeHttpClient httpClient, KnowledgeProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * Upload documents to a user's personal knowledge base using the default token.
     * Dataset name follows the pattern: user_{userId}
     * Creates the dataset automatically if it doesn't exist.
     *
     * @param userId user identifier
     * @param files  files to upload
     * @return list of batch results (one per batch)
     */
    public List<String> uploadToUserDataset(String userId, List<MultipartFile> files) {
        String datasetName = properties.getUserDatasetPrefix() + userId;
        return uploadDocuments(datasetName, files);
    }

    /**
     * Upload documents to a user's personal knowledge base using a user-specific token.
     * Logs in as the specified user via SSO to get their token,
     * so the dataset is created under that user's account.
     * Dataset name follows the pattern: user_{userId}
     *
     * @param userId   user identifier
     * @param username the user's username for SSO login
     * @param email    the user's email for SSO login
     * @param files    files to upload
     * @return list of batch results (one per batch)
     */
    public List<String> uploadToUserDataset(String userId, String username, String email,
                                             List<MultipartFile> files) {
        String datasetName = properties.getUserDatasetPrefix() + userId;
        return uploadDocuments(datasetName, files, username, email);
    }

    /**
     * Upload documents to the public knowledge base.
     * Creates the dataset automatically if it doesn't exist.
     *
     * @param files files to upload
     * @return list of batch results (one per batch)
     */
    public List<String> uploadToPublicDataset(List<MultipartFile> files) {
        return uploadDocuments(properties.getPublicDatasetName(), files);
    }

    /**
     * Upload documents to a named knowledge base using the default token.
     * Creates the dataset automatically if it doesn't exist.
     * Automatically splits into batches if more than maxFilesPerBatch files.
     *
     * @param datasetName name of the knowledge base
     * @param files       files to upload
     * @return list of batch results (one per batch)
     */
    public List<String> uploadDocuments(String datasetName, List<MultipartFile> files) {
        return uploadDocuments(datasetName, files, null, null);
    }

    /**
     * Upload documents to a named knowledge base using a user-specific token.
     * Creates the dataset automatically if it doesn't exist.
     * Automatically splits into batches if more than maxFilesPerBatch files.
     *
     * @param datasetName name of the knowledge base
     * @param files       files to upload
     * @param username    the user's username for SSO login (null for default token)
     * @param email       the user's email for SSO login (null for default token)
     * @return list of batch results (one per batch)
     */
    public List<String> uploadDocuments(String datasetName, List<MultipartFile> files,
                                         String username, String email) {
        validateFiles(files);

        log.info("Uploading {} files to dataset '{}'", files.size(), datasetName);

        List<List<MultipartFile>> batches = splitIntoBatches(files);
        log.info("Split into {} batches", batches.size());

        String datasetId = findDatasetByName(datasetName, username, email);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < batches.size(); i++) {
            List<MultipartFile> batch = batches.get(i);
            log.info("Processing batch {}/{} with {} files", i + 1, batches.size(), batch.size());

            List<FileUploadResponse> uploadedFiles = httpClient.uploadFiles(batch, username, email);
            List<String> fileIds = new ArrayList<>();
            for (FileUploadResponse uploadResponse : uploadedFiles) {
                fileIds.add(uploadResponse.getId());
            }

            if (datasetId == null) {
                log.info("Dataset '{}' not found, creating with initial documents", datasetName);
                datasetId = httpClient.initDatasetWithDocuments(datasetName, fileIds, username, email);
                results.add(datasetId);
            } else {
                String batchResult = httpClient.createDocumentInDataset(datasetId, fileIds, username, email);
                results.add(batchResult);
            }
        }

        return results;
    }

    /**
     * Create a knowledge base using the default token.
     *
     * @param name name of the knowledge base
     * @return created dataset info
     */
    public DatasetResponse createDataset(String name) {
        return createDataset(name, null, null);
    }

    /**
     * Create a knowledge base using a user-specific token.
     *
     * @param name     name of the knowledge base
     * @param username the user's username for SSO login
     * @param email    the user's email for SSO login
     * @return created dataset info
     */
    public DatasetResponse createDataset(String name, String username, String email) {
        log.info("Creating dataset: {}", name);
        DatasetResponse response = httpClient.createDataset(name, username, email);
        return response;
    }

    /**
     * Create a user's personal knowledge base using the default token.
     * Dataset name follows the pattern: user_{userId}
     *
     * @param userId user identifier
     * @return created dataset info
     */
    public DatasetResponse createUserDataset(String userId) {
        return createUserDataset(userId, null, null);
    }

    /**
     * Create a user's personal knowledge base using a user-specific token.
     * Logs in as the specified user via SSO to get their token,
     * so the dataset is created under that user's account (truly private).
     * Dataset name follows the pattern: user_{userId}
     *
     * @param userId   user identifier
     * @param username the user's username for SSO login
     * @param email    the user's email for SSO login
     * @return created dataset info
     */
    public DatasetResponse createUserDataset(String userId, String username, String email) {
        String datasetName = properties.getUserDatasetPrefix() + userId;
        return createDataset(datasetName, username, email);
    }

    /**
     * Create the public knowledge base using the default token.
     * Dataset name defaults to 'public_dataset' (configurable via knowledge.public-dataset-name).
     *
     * @return created dataset info
     */
    public DatasetResponse createPublicDataset() {
        return createDataset(properties.getPublicDatasetName());
    }

    /**
     * Delete a document from a knowledge base.
     *
     * @param datasetId  ID of the knowledge base
     * @param documentId ID of the document to delete
     */
    public void deleteDocument(String datasetId, String documentId) {
        log.info("Deleting document {} from dataset {}", documentId, datasetId);
        httpClient.deleteDocument(datasetId, documentId);
    }

    /**
     * Delete a knowledge base.
     *
     * @param datasetId ID of the knowledge base to delete
     */
    public void deleteDataset(String datasetId) {
        log.info("Deleting dataset {}", datasetId);
        httpClient.deleteDataset(datasetId);
    }

    private String findDatasetByName(String datasetName, String username, String email) {
        try {
            DatasetListResponse response = httpClient.listDatasets(datasetName, 1, 100, username, email);
            if (response.getData() != null) {
                for (DatasetResponse dataset : response.getData()) {
                    if (datasetName.equals(dataset.getName())) {
                        return dataset.getId();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to search for dataset '{}': {}", datasetName, e.getMessage());
        }
        return null;
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new KnowledgeException("File list cannot be empty");
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new KnowledgeException("File cannot be null or empty");
            }
            if (file.getSize() > properties.getMaxFileSize()) {
                throw new KnowledgeException(
                        "File '" + file.getOriginalFilename() + "' size "
                                + file.getSize() + " exceeds maximum " + properties.getMaxFileSize());
            }
        }
    }

    private List<List<MultipartFile>> splitIntoBatches(List<MultipartFile> files) {
        List<List<MultipartFile>> batches = new ArrayList<>();
        int maxPerBatch = properties.getMaxFilesPerBatch();

        for (int i = 0; i < files.size(); i += maxPerBatch) {
            int end = Math.min(i + maxPerBatch, files.size());
            batches.add(new ArrayList<>(files.subList(i, end)));
        }

        return batches;
    }
}
