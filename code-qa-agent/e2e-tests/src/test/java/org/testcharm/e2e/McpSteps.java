package org.testcharm.e2e;

import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testcharm.dal.Assertions.expect;

public class McpSteps {

    private final List<String> mcpAnswers = new ArrayList<>();

    @当("向MCP服务发送问题{string}")
    public void askMcp(String question) throws Exception {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:13001")
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        try {
            client.initialize();
            var result = client.callTool(
                    new McpSchema.CallToolRequest("ask_repo_question", Map.of("question", question)));

            String answer = null;
            if (result.content() != null && !result.content().isEmpty()) {
                var content = result.content().get(0);
                if (content instanceof McpSchema.TextContent textContent) {
                    answer = textContent.text();
                }
            }
            mcpAnswers.add(answer != null ? answer : result.toString());
        } finally {
            client.closeGracefully();
        }
    }

    @那么("MCP回答应为{string}")
    public void verifyMcpAnswer(String expected) {
        expect(mcpAnswers).should("""
                : [ ... '%s' ... ]
                """.formatted(expected));
    }

    @那么("MCP回答应包含{string}")
    public void verifyMcpAnswerContains(String expected) {
        expect(mcpAnswers).should("""
                : [ ... ~= '%s' ... ]
                """.formatted(expected));
    }
}
