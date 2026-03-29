package org.testcharm.e2e.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class WaitingTime {

    private long seconds;
}
