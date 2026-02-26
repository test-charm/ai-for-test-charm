package com.testcharm.cucumber.spec;

import com.github.leeonky.jfactory.Spec;
import com.testcharm.cucumber.TempFiles;
import com.testcharm.cucumber.entity.CmdArg;

public class Arguments {

    public static class 命令行参数 extends Spec<CmdArg> {
        @Override
        public void main() {
            var tempFolder = TempFiles.tempFiles().getPath();
            property("src").value(tempFolder.resolve("input").toString());
            property("dst").value(tempFolder.resolve("output", "TestCharm").toString());
            property("disableUpload").value(false);
            property("retryCount").value(3);
        }
    }
}
