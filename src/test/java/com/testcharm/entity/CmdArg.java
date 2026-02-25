package com.testcharm.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CmdArg {
    private String src, dst;
    private boolean disableUpload;
    private int retryCount;
}
