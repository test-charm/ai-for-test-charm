package org.testcharm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.nio.file.Paths;
import java.util.Arrays;

@SpringBootApplication
@EnableFeignClients
public class Application implements CommandLineRunner {

    @Autowired
    private KbProcessor kbProcessor;

    @Autowired
    private DifyKbUploader difyKbUploader;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        var src = args[0];
        var dst = args[1];
        boolean disableUpload = Arrays.asList(args).contains("--disable-upload");
        int retryCount = Arrays.stream(args)
                .filter(a -> a.startsWith("--retry-count="))
                .findFirst()
                .map(a -> Integer.parseInt(a.substring("--retry-count=".length())))
                .orElse(3);
        kbProcessor.process(src, dst);
        if (!disableUpload) {
            String datasetName = Paths.get(dst).getFileName().toString();
            difyKbUploader.setRetryCount(retryCount);
            difyKbUploader.upload(datasetName, Paths.get(dst));
        }
    }
}
