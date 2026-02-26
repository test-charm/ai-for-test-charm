package org.testcharm.e2e.spec;

import com.github.leeonky.jfactory.Spec;
import org.testcharm.e2e.TempFiles;
import org.testcharm.e2e.entity.CmdArg;

public class Arguments {

    public static class 命令行参数 extends Spec<CmdArg> {
        @Override
        public void main() {
            var tempFolder = TempFiles.tempFiles().getPath();
            property("src").value(tempFolder.resolve("input").toString());
            property("dst").value(tempFolder.resolve("output", "TestCharm").toString());
            property("disableUpload").value(false);
            property("uploadOnly").value(false);
            property("retryCount").value(3);
        }
    }
}
