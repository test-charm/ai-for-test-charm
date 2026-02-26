package org.testcharm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;

@SpringBootApplication
@EnableFeignClients
@Command
public class Application implements CommandLineRunner {

    @Autowired
    private KbProcessor kbProcessor;

    @Autowired
    private DifyKbUploader difyKbUploader;

    @Parameters(index = "0")
    private String src;

    @Parameters(index = "1")
    private String dst;

    @Option(names = "--disable-upload")
    private boolean disableUpload;

    @Option(names = "--retry-count", defaultValue = "3")
    private int retryCount;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {
        new CommandLine(this).setUnmatchedArgumentsAllowed(true).parseArgs(args);
        kbProcessor.process(src, dst);
        if (!disableUpload) {
            String datasetName = Paths.get(dst).getFileName().toString();
            difyKbUploader.setRetryCount(retryCount);
            difyKbUploader.upload(datasetName, Paths.get(dst));
        }
    }
}
