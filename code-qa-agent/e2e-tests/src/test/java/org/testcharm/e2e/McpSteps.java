package org.testcharm.e2e;

import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.testcharm.dal.Assertions.expect;

public class McpSteps {

    private final List<String> mcpAnswers = new ArrayList<>();

    @当("向MCP服务发送问题{string}")
    public void askMcp(String question) throws Exception {
        String jsonRpc = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "tools/call")
                .put("params", new JSONObject()
                        .put("name", "ask_repo_question")
                        .put("arguments", new JSONObject()
                                .put("question", question)))
                .put("id", 1)
                .toString();

        URI uri = URI.create("http://localhost:13001/mcp");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonRpc.getBytes("UTF-8"));
        }

        String contentType = conn.getContentType();
        String responseText;
        if (contentType != null && contentType.contains("text/event-stream")) {
            responseText = readSse(conn);
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                responseText = sb.toString();
            }
        }
        conn.disconnect();

        // Parse final result from response
        String answer = extractAnswer(responseText);
        mcpAnswers.add(answer != null ? answer : responseText);
    }

    private String readSse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private String extractAnswer(String sseText) {
        String lastData = null;
        for (String line : sseText.split("\n")) {
            if (line.startsWith("data: ")) {
                lastData = line.substring(6);
            }
        }
        if (lastData != null) {
            try {
                JSONObject json = new JSONObject(lastData);
                JSONObject result = json.optJSONObject("result");
                if (result != null) {
                    JSONArray content = result.optJSONArray("content");
                    if (content != null && content.length() > 0) {
                        return content.getJSONObject(0).optString("text");
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
