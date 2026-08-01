package org.testcharm.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.cucumber.java.After;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.而且;
import io.cucumber.java.zh_cn.那么;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.testcharm.cucumber.restful.RestfulStep;
import org.testcharm.cucumber.restful.extensions.PathVariableReplacement;
import org.testcharm.jfactory.JFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

import static org.testcharm.dal.Assertions.expect;

@Slf4j
public class SocketIOSteps {

    private static final Pattern THREAD_ID_PATTERN = Pattern.compile("\"thread_id\":\"([^\"]+)\"");
    private static final Pattern THREAD_ID_CAMEL_PATTERN = Pattern.compile("threadId=([a-f0-9\\-]+)");

    @Autowired
    private SocketIOClient client;

    @当("连接 Socket.IO:")
    public void connect(String authJson) throws Exception {
        String resolved = resolveVariables(authJson.trim());
        JSONObject authObj = new JSONObject(resolved);
        Map<String, String> auth = new HashMap<>();
        for (Iterator<String> it = authObj.keys(); it.hasNext(); ) {
            String key = it.next();
            auth.put(key, authObj.optString(key));
        }
        client.connect(auth);
    }

    @当("发送事件 {string}")
    public void emitEvent(String eventName) {
        client.emit(eventName);
    }

    @当("发送事件 {string}:")
    public void emitEventWithData(String eventName, String dataJson) {
        String resolved = resolveVariables(dataJson.trim());
        Object data;
        try {
            if (resolved.startsWith("{")) {
                JSONObject obj = new JSONObject(resolved);
                Map<String, Object> map = new HashMap<>();
                for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                    String key = it.next();
                    map.put(key, obj.opt(key));
                }
                data = map;
            } else if (resolved.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(resolved);
                List<Object> list = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.opt(i));
                }
                data = list;
            } else {
                data = resolved;
            }
        } catch (org.json.JSONException e) {
            throw new RuntimeException("Failed to parse Socket.IO event data: " + resolved, e);
        }
        client.emit(eventName, data);
    }

    @那么("收到的 Socket.IO 事件应满足:")
    public void verifyEvents(String dalExpression) {
        for (Map<String, Object> event : client.getReceivedEvents()) {
            Object data = event.get("data");
            if (data != null) {
                extractThreadId(data.toString());
            }
        }
        expect(client).should(dalExpression);
    }

    @当("断开 Socket.IO 连接")
    public void disconnectSocketIO() {
        client.clear();
    }

    @After(order = 999)
    public void disconnect() {
        client.clear();
    }

    private void extractThreadId(String text) {
        var matcher = THREAD_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            PathVariableReplacement.replacements.put("thread-id", matcher.group(1));
            return;
        }
        matcher = THREAD_ID_CAMEL_PATTERN.matcher(text);
        if (matcher.find()) {
            PathVariableReplacement.replacements.put("thread-id", matcher.group(1));
        }
    }

    private String resolveVariables(String json) {
        for (var entry : PathVariableReplacement.replacements.entrySet()) {
            json = json.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return json;
    }

    @Autowired
    private RestfulStep restfulStep;

    @SneakyThrows
    @当("用户发送消息{string}")
    public void 用户发送消息(String message) {
        restfulStep.postInJson("/set-session-cookie", """
                  {
                    "session_id": "${session-id}"
                  }
                """);
        restfulStep.responseShouldBe("""
                  : {
                    code=200
                    body.json.message='Session cookie set'
                  }
                """);
        connect("""
                  {
                    "clientType": "webapp",
                    "sessionId": "${session-id}",
                    "userEnv": "{}"
                  }
                """);
        emitEvent("connection_successful");
        emitEventWithData("client_message", """
                  {
                    "message": {
                      "id": "${message-id}",
                      "createdAt": "2026-07-09T00:00:00.000Z",
                      "output": "%s",
                      "name": "joseph"
                    }
                  }
                """.formatted(message));

        // Capture the threadId from the first new_message event for later use by EvalSteps
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            var threadId = client.getReceivedEvents().stream()
                    .filter(e -> "new_message".equals(e.get("name")))
                    .map(e -> e.get("data"))
                    .filter(d -> d instanceof Map<?, ?>)
                    .map(d -> ((Map<?, ?>) d).get("threadId"))
                    .filter(t -> t instanceof String)
                    .map(Object::toString)
                    .findFirst()
                    .orElse(null);
            if (threadId != null) {
                PathVariableReplacement.replacements.put("eval-thread-id", threadId);
                break;
            }
            Thread.sleep(200);
        }
    }

    @SneakyThrows
    @当("用户继续发送消息{string}")
    public void 用户继续发送消息(String message) {
        PathVariableReplacement.replacements.put("message-id", UUID.randomUUID().toString());
        emitEventWithData("client_message", """
                  {
                    "message": {
                      "id": "${message-id}",
                      "createdAt": "2026-07-09T00:00:01.000Z",
                      "output": "%s",
                      "name": "joseph"
                    }
                  }
                """.formatted(message));
    }

    @当("仅发送消息{string}")
    public void 仅发送消息(String message) {
        emitEvent("connection_successful");
        emitEventWithData("client_message", """
                  {
                    "message": {
                      "id": "${message-id}",
                      "createdAt": "2026-07-09T00:00:00.000Z",
                      "output": "%s",
                      "name": "joseph"
                    }
                  }
                """.formatted(message));
    }

    @SneakyThrows
    @当("收齐回复")
    public void 收齐回复() {
        long deadline = System.currentTimeMillis() + 600_000;
        String lastContent = "";
        int lastEventCount = 0;
        int loopCount = 0;
        while (System.currentTimeMillis() < deadline) {
            loopCount++;
            String content = queryLastAssistantMessage();
            int currentEventCount = client.getReceivedEvents().size();
            if (loopCount <= 5 || loopCount % 10 == 0) {
                log.info("收齐回复 loop #{} content={} eventCount={}",
                        loopCount,
                        content != null ? ("'" + (content.length() > 80 ? content.substring(0, 80) + "..." : content) + "'") : "null",
                        currentEventCount);
            }
            // Every 15 loops with no content, dump event diagnostics to help debug flaky timeouts
            if (content == null && loopCount % 15 == 0) {
                var msgEvents = new ArrayList<String>();
                for (var e : client.getReceivedEvents()) {
                    String name = (String) e.get("name");
                    if (!"new_message".equals(name) && !"update_message".equals(name)) continue;
                    Object d = e.get("data");
                    String type = "?";
                    String outPreview = "?";
                    if (d instanceof Map<?, ?> m) {
                        Object typeObj = m.get("type");
                        type = typeObj != null ? typeObj.toString() : "?";
                        Object outObj = m.get("output");
                        String outStr = outObj != null ? outObj.toString() : "?";
                        outPreview = outStr.length() > 60 ? outStr.substring(0, 60) + "..." : outStr;
                    }
                    msgEvents.add(name + " type=" + type + " output='" + outPreview.replace("\n", "\\n") + "'");
                }
                log.info("收齐回复 diagnostic loop #{}: msgEvents=[{}]", loopCount, String.join(" | ", msgEvents));
            }
            if (content != null && !content.isBlank()) {
                if (lastEventCount == currentEventCount && loopCount > 1) {
                    // Event count is stable: no new events arriving.
                    // Wait 5 more seconds to ensure the response is complete,
                    // then accept. Using eventCount stability avoids the issue
                    // where queryLastAssistantMessage returns null on retry.
                    long stableUntil = System.currentTimeMillis() + 5000;
                    while (System.currentTimeMillis() < stableUntil) {
                        Thread.sleep(1000);
                        if (client.getReceivedEvents().size() != currentEventCount) {
                            lastEventCount = client.getReceivedEvents().size();
                            content = queryLastAssistantMessage();
                            if (content != null && !content.isBlank()) {
                                lastContent = content;
                            }
                            break;
                        }
                    }
                    if (System.currentTimeMillis() >= stableUntil) {
                        lastAgentReply = content;
                        break;
                    }
                }
                lastEventCount = currentEventCount;
                if (content != null && !content.isBlank()) {
                    lastContent = content;
                }
            }
            Thread.sleep(2000);
        }

        if (lastAgentReply == null) {
            log.info("receivedEvents: {}", new ObjectMapper()
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .writeValueAsString(client.getReceivedEvents()));
            throw new AssertionError("Agent did not respond within 600s. Check agent logs.");
        }

        // Strip the timing footer appended by app.py ("\n\n---\n⏱️ 耗时 ...")
        int footerIdx = lastAgentReply.lastIndexOf("---");
        if (footerIdx > 0 && lastAgentReply.substring(footerIdx).contains("⏱️")) {
            lastAgentReply = lastAgentReply.substring(0, footerIdx).stripTrailing();
        }
    }

    @Value("${embedding.base-url:http://localhost:18002}")
    private String embeddingBaseUrl;

    @Value("${app.db.url}")
    private String dbUrl;

    @Value("${app.db.username}")
    private String dbUsername;

    @Value("${app.db.password}")
    private String dbPassword;

    private String lastAgentReply;

    @而且("回复蕴含度应大于 {double}:")
    public void 回复蕴含度应大于(double threshold, String goldenText) {
        if (lastAgentReply == null) {
            throw new AssertionError("No reply captured. Call 收齐回复 before 回复蕴含度.");
        }
        log.info("lastAgentReply {}", lastAgentReply);

        double score = callNliAggregated(lastAgentReply, goldenText.strip());

        log.info("Containment ratio={} threshold={}", score, threshold);

        if (score <= threshold) {
            throw new AssertionError(String.format(
                    "NLI entailment score %.4f <= %.2f threshold. Key factual claims are not entailed by the reply.", score, threshold));
        }
    }

     @SneakyThrows
     private double callNliAggregated(String actual, String golden) {
         // Split golden into individual factual claims (separated by blank lines)
         String[] claims = golden.split("\\n{2,}");
         if (claims.length <= 1) {
             claims = golden.split("(?<=[。])\\s*");
         }

         var restfulStep = new RestfulStep();
         restfulStep.setJFactory(new JFactory());
         restfulStep.setBaseUrl(embeddingBaseUrl);

         var body = new org.json.JSONObject();
         body.put("claims", claims);
         body.put("reply", actual);
         restfulStep.postInJson("/containment", body.toString());

         var scores = restfulStep.response("body.json.scores");
         log.info("Containment scores: {}", scores);

         return ((BigDecimal) restfulStep.response("body.json.ratio")).doubleValue();
     }

    @SneakyThrows
    private String queryLastAssistantMessage() {
        // First try: find assistant_message from new_message/update_message events
        var outputs = client.getReceivedEvents().stream()
                .filter(e -> "new_message".equals(e.get("name")) || "update_message".equals(e.get("name")))
                .map(e -> e.get("data"))
                .filter(d -> d instanceof Map<?, ?>)
                .map(d -> (Map<?, ?>) d)
                .filter(msgMap -> "assistant_message".equals(msgMap.get("type")))
                .map(msgMap -> (String) msgMap.get("output"))
                .filter(output -> output != null && !output.isBlank()
                        && !output.contains("我已准备好分析代码库，请问你想了解什么？"))
                .reduce((first, second) -> second)
                .orElse(null);
        if (outputs != null) {
            return outputs;
        }

        // Fallback: reconstruct from stream_token events.
        // This handles the case where msg.send() fails to deliver the final
        // new_message (e.g., due to large payloads with Chainlit HTTP polling).
        var tokenStreams = new LinkedHashMap<String, StringBuilder>();
        String firstStepId = null;
        boolean seenAssistantStream = false;
        for (var e : client.getReceivedEvents()) {
            if (!"stream_start".equals(e.get("name")) && !"stream_token".equals(e.get("name")))
                continue;
            Object d = e.get("data");
            if (!(d instanceof Map<?, ?> m)) continue;
            Object stepIdObj = m.get("id");
            if (!(stepIdObj instanceof String stepId)) continue;

            if ("stream_start".equals(e.get("name"))) {
                // Mark the first stream_start after the greeting as the assistant stream
                if (!seenAssistantStream) {
                    firstStepId = stepId;
                    seenAssistantStream = true;
                }
            } else {
                // stream_token
                Object tokenObj = m.get("token");
                if (!(tokenObj instanceof String token) || token.isEmpty()) continue;
                Object isSeqObj = m.get("isSequence");
                boolean isSeq = isSeqObj instanceof Boolean b && b;
                var sb = tokenStreams.computeIfAbsent(stepId, k -> new StringBuilder());
                if (isSeq) {
                    sb.setLength(0);
                }
                sb.append(token);
            }
        }

        // Use the content from the first assistant stream (after greeting)
        if (firstStepId != null) {
            var sb = tokenStreams.get(firstStepId);
            if (sb != null && !sb.isEmpty()) {
                String content = sb.toString().strip();
                if (!content.isBlank() && !content.contains("我已准备好分析代码库，请问你想了解什么？")) {
                    return content;
                }
            }
        }

        // Final fallback: use the last stream with non-empty content
        for (var entry : tokenStreams.entrySet()) {
            String content = entry.getValue().toString().strip();
            if (!content.isBlank() && !content.contains("我已准备好分析代码库，请问你想了解什么？")) {
                return content;
            }
        }

        // Database fallback: query Chainlit steps table directly for the last assistant message.
        // This handles cases where Socket.IO polling fails to deliver new_message/stream_token
        // events (e.g., due to large payloads or connection issues).
        return queryLastMessageFromDb();
    }

    private String queryLastMessageFromDb() {
        String threadId = PathVariableReplacement.replacements.get("eval-thread-id");
        if (threadId == null) {
            log.debug("queryLastMessageFromDb: no eval-thread-id set, skipping DB fallback");
            return null;
        }
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT output FROM steps WHERE \"threadId\" = ? AND type = 'assistant_message' "
                             + "AND output IS NOT NULL AND length(output) > 200 "
                             + "ORDER BY \"createdAt\" DESC LIMIT 1")) {
            stmt.setString(1, threadId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String output = rs.getString("output");
                    String preview = output != null && output.length() > 100
                            ? output.substring(0, 100).replace("\n", "\\n") + "..."
                            : String.valueOf(output);
                    log.info("queryLastMessageFromDb thread={} chars={} preview={}",
                            threadId, output != null ? output.length() : 0, preview);
                    if (output != null && !output.contains("我已准备好分析代码库，请问你想了解什么？")) {
                        return output;
                    }
                } else {
                    log.debug("queryLastMessageFromDb: no qualifying message for thread={}", threadId);
                }
            }
        } catch (Exception e) {
            log.warn("queryLastMessageFromDb failed: {}", e.toString());
        }
        return null;
    }

}
