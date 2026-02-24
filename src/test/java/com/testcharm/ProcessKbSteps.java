package com.testcharm;

import com.github.leeonky.dal.Assertions;
import com.github.leeonky.jfactory.cucumber.JData;
import com.github.leeonky.jfactory.cucumber.Table;
import com.testcharm.entity.CmdArg;
import io.cucumber.java.Before;
import io.cucumber.java.zh_cn.并且;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

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
        List<CmdArg> args = jData.prepare(traitAndSpec, table);
        CmdArg cmdArg = args.get(0);
        var argList = new ArrayList<>(List.of(cmdArg.getSrc(), cmdArg.getDst()));
        if (cmdArg.isDisableUpload()) {
            argList.add("--disable-upload");
        }
        Application.main(argList.toArray(new String[0]));
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
