package org.testcharm.e2e;

import com.github.leeonky.dal.Assertions;
import com.github.leeonky.jfactory.cucumber.JData;
import com.github.leeonky.jfactory.cucumber.Table;
import io.cucumber.java.Before;
import io.cucumber.java.zh_cn.并且;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcharm.e2e.entity.CmdArg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProcessKbSteps {

    @Autowired
    private JData jData;

    @Autowired
    private TempFiles tempFiles;

    private String dstPath;

    @Before
    public void cleanUp() {
        TempFiles.tempFiles().clean();
    }

    @SneakyThrows
    @当("用以下{string}执行时:")
    public void executeWith(String traitAndSpec, Table table) {
        execute(traitAndSpec, table);
    }

    @SneakyThrows
    private void execute(String traitAndSpec, Table table) {
        List<CmdArg> args = jData.prepare(traitAndSpec, table);
        CmdArg cmdArg = args.get(0);
        var argList = new ArrayList<>(List.of(cmdArg.getSrc(), cmdArg.getDst()));
        if (cmdArg.isDisableUpload()) {
            argList.add("--disable-upload");
        }
        if (cmdArg.isUploadOnly()) {
            argList.add("--upload-only");
        }
        argList.add("--retry-count=" + cmdArg.getRetryCount());
        argList.add("--spring.profiles.active=test");

        var command = new ArrayList<>(List.of("java", "-jar", findJarPath()));
        command.addAll(argList);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("jar process exited with code {}", exitCode);
        }
    }

    @SneakyThrows
    private String findJarPath() {
        try (var files = Files.list(Path.of("build/libs"))) {
            return files.filter(p -> p.toString().endsWith(".jar") && !p.toString().endsWith("-plain.jar"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No jar found in build/libs"))
                    .toString();
        }
    }

    @那么("输出的文件应为:")
    public void verifyOutputFiles(String docString) {
        Assertions.expect(tempFiles.getAbsolutePath("output")).should(docString.replaceAll("'''", "\"\"\""));
    }

    @并且("数据应为ex:")
    public void 数据应为ex(String docString) {
        jData.allDataShouldBe(docString.replaceAll("'''", "\"\"\""));
    }
}
