package org.testcharm;

import feign.FeignException;
import feign.form.FormData;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Component
public class DifyKbUploader {
    @Autowired(required = false)
    private DifyApiClient difyApiClient;
    @Setter
    private int retryCount = 3;

    @Autowired
    private Waiting waiting;

    @SneakyThrows
    public void upload(String datasetName, Path outputDir) {
        String datasetId = findDatasetId(datasetName);
        try (Stream<Path> files = Files.walk(outputDir)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().endsWith("_done.txt"))
                    .forEach(file -> uploadFile(datasetId, file));
        }
    }

    private String findDatasetId(String datasetName) {
        return callWithRetry(() -> difyApiClient.listDatasets()).getData().get(0).getId();
    }

    private String findDocumentId(String datasetId, String keyword) {
        var data = callWithRetry(() -> difyApiClient.listDocuments(datasetId, 100, keyword)).getData();
        return data.isEmpty() ? null : data.get(0).getId();
    }

    @SneakyThrows
    private void uploadFile(String datasetId, Path file) {
        String fileName = file.getFileName().toString();
        String documentId = findDocumentId(datasetId, fileName);
        byte[] fileContent = Files.readAllBytes(file);
        FormData formData = new FormData("text/plain", fileName, fileContent);
        try {
            executeWithRetry(() -> {
                if (documentId != null) {
                    difyApiClient.updateDocumentByFile(datasetId, documentId, formData);
                } else {
                    difyApiClient.createDocumentByFile(datasetId, formData);
                }
            });
            log.info("上传成功: {}", fileName);
            renameToDone(file);
        } catch (FeignException e) {
            log.error("上传失败: {}, response: {}", fileName, e.contentUTF8(), e);
            throw e;
        } catch (Exception e) {
            log.error("上传失败: {}", fileName, e);
            throw e;
        }
        waiting.sleepSeconds(1);
    }

    private <T> T callWithRetry(Supplier<T> action) {
        int attempts = 0;
        while (true) {
            try {
                return action.get();
            } catch (FeignException e) {
                attempts++;
                if (e.status() < 500 || attempts >= retryCount) {
                    throw e;
                }
            }
        }
    }

    private void executeWithRetry(Runnable action) {
        callWithRetry(() -> {
            action.run();
            return null;
        });
    }

    @SneakyThrows
    private void renameToDone(Path file) {
        String fileName = file.getFileName().toString();
        String doneName = fileName.replaceFirst("\\.txt$", "_done.txt");
        Files.move(file, file.resolveSibling(doneName));
    }
}
