package org.testcharm.e2e.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class LlmResponse {

    private int created;
    private List<Choice> choices = new ArrayList<>();

    @Getter
    @Setter
    public static class Choice {
        private Message message;

        @Getter
        @Setter
        public static class Message {
            private String role, content;
            @JsonProperty("tool_calls")
            private List<ToolCall> toolCalls = new ArrayList<>();

            @Getter
            @Setter
            public static class ToolCall {
                private Function function;

                @Getter
                @Setter
                public static class Function {
                    private String name, arguments;
                }
            }
        }
    }
}
