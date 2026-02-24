package com.testcharm;

import feign.form.FormData;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class DifyKbUploader {
    private final DifyApiClient difyApiClient;

    public DifyKbUploader(DifyApiClient difyApiClient) {
        this.difyApiClient = difyApiClient;
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
        return difyApiClient.listDocuments(datasetId, 100, keyword).getData().get(0).getId();
    }

    @SneakyThrows
    private void uploadFile(String datasetId, Path file) {
        String fileName = file.getFileName().toString();
        String documentId = findDocumentId(datasetId, fileName);
        byte[] fileContent = Files.readAllBytes(file);
        FormData formData = new FormData("application/octet-stream", fileName, fileContent);
        difyApiClient.updateDocumentByFile(datasetId, documentId, formData);
    }
}
