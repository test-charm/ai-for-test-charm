package org.testcharm.e2e.spec;

import org.testcharm.jfactory.Spec;

public class LlmResponses {

    public static class LlmResponse extends Spec<org.testcharm.e2e.dto.LlmResponse> {
        @Override
        public void main() {
            property("created").defaultValue(1752050400);
            property("choices[]").apply("Choice");
        }
    }

    public static class Choice extends Spec<org.testcharm.e2e.dto.LlmResponse.Choice> {
        @Override
        public void main() {
            property("message").apply("Message");
        }
    }

    public static class Message extends Spec<org.testcharm.e2e.dto.LlmResponse.Choice.Message> {
        @Override
        public void main() {
            property("role").defaultValue("assistant");
            property("toolCalls[]").apply("ToolCall");
        }
    }

    public static class ToolCall extends Spec<org.testcharm.e2e.dto.LlmResponse.Choice.Message.ToolCall> {
        @Override
        public void main() {
            property("function").apply("Function");
        }
    }

    public static class Function extends Spec<org.testcharm.e2e.dto.LlmResponse.Choice.Message.ToolCall.Function> {
    }

    public static class ListDirectory extends Function {
        @Override
        public void main() {
            property("name").defaultValue("list_directory");
            property("arguments").defaultValue("{\"path\":\".\"}");
        }
    }

    public static class ReadFile extends Function {
        @Override
        public void main() {
            property("name").defaultValue("read_file");
            property("arguments").defaultValue("{\"file_path\":\"app.py\"}");
        }
    }
}
