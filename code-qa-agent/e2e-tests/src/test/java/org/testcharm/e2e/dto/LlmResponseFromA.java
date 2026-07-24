package org.testcharm.e2e.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LlmResponseFromA {

    private List<Content> content;

    @Getter
    @Setter
    public static class Content {

        private String type, name, text;
        private Input input;

        @Getter
        @Setter
        public static class Input {
            private String path;
        }
    }
}
