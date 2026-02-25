package com.testcharm;

import feign.FeignException;
import feign.form.FormData;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DifyKbUploader {
    private final DifyApiClient difyApiClient;
    private final int retryCount;

    public DifyKbUploader(DifyApiClient difyApiClient, int retryCount) {
        this.difyApiClient = difyApiClient;
        this.retryCount = retryCount;
    }

    @SneakyThrows
    public void upload(String datasetName, Path outputDir) {
        String datasetId = findDatasetId(datasetName);
        try (Stream<Path> files = Files.walk(outputDir)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> uploadFile(datasetId, file));
        }
    }

    private String findDatasetId(String datasetName) {
        return difyApiClient.listDatasets().getData().get(0).getId();
    }

    private String findDocumentId(String datasetId, String keyword) {
        var data = difyApiClient.listDocuments(datasetId, 100, keyword).getData();
        return data.isEmpty() ? null : data.get(0).getId();
    }

    @SneakyThrows
    private void uploadFile(String datasetId, Path file) {
        String fileName = file.getFileName().toString();
        String documentId = findDocumentId(datasetId, fileName);
        byte[] fileContent = Files.readAllBytes(file);
        FormData formData = new FormData("text/plain", fileName, fileContent);
        executeWithRetry(() -> {
            if (documentId != null) {
                difyApiClient.updateDocumentByFile(datasetId, documentId, formData);
            } else {
                difyApiClient.createDocumentByFile(datasetId, formData);
            }
        });
    }

    private void executeWithRetry(Runnable action) {
        int attempts = 0;
        while (true) {
            try {
                action.run();
                return;
            } catch (FeignException e) {
                attempts++;
                if (e.status() < 500 || attempts >= retryCount) {
                    throw e;
                }
            }
        }
    }
}
