package com.testcharm.cucumber;

import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class TempFiles {
    private static final TempFiles TEMP_FILES = new TempFiles();
    private final String path = "/tmp/ai_for_test_charm";

    @SneakyThrows
    private TempFiles() {
        Files.createDirectories(Paths.get(path));
    }

    public static TempFiles tempFiles() {
        return TEMP_FILES;
    }

    public void createWithContent(String fileName, String content) throws IOException {
        Files.writeString(createPath(fileName), content);
    }

    @SneakyThrows
    private Path createPath(String fileName) {
        Path path = getAbsolutePath(fileName);
        if (!Files.exists(path.getParent()))
            Files.createDirectories(path.getParent());
        return path;
    }

    public void createWithContent(String fileName, byte[] content) throws IOException {
        Files.write(createPath(fileName), content);
    }

    public Path getAbsolutePath(String fileName) {
        return Paths.get(path, fileName);
    }

    public Path getPath() {
        return Paths.get(path);
    }

    @SneakyThrows
    public String readContent(String file) {
        return new String(Files.readAllBytes(getAbsolutePath(file)));
    }

    @SneakyThrows
    public byte[] readBinaryContent(String file) {
        return Files.readAllBytes(getAbsolutePath(file));
    }

    @SneakyThrows
    public void clean() {
        cleanIfExists("");
    }

    private void cleanIfExists(String folder) throws IOException {
        File directory = new File(path + folder);
        if (directory.exists()) {
            FileUtils.cleanDirectory(directory);
        } else {
            directory.mkdirs();
        }
    }

}
