package org.testcharm.e2e;

import io.cucumber.java.After;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.testcharm.cucumber.restful.extensions.PathVariableReplacement;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.testcharm.dal.Assertions.expect;

public class SocketIOSteps {

    private static final Pattern THREAD_ID_PATTERN = Pattern.compile("\"thread_id\":\"([^\"]+)\"");

    @Autowired
    private ApplicationSteps applicationSteps;

    @Value("${app.base-url}")
    private String baseUrl;

    private SocketIOClient client;

    @当("连接 Socket.IO:")
    public void connect(String authJson) throws Exception {
        client = new SocketIOClient();
        String resolved = resolveVariables(authJson.trim());
        JSONObject authObj = new JSONObject(resolved);
        Map<String, String> auth = new HashMap<>();
        for (Iterator<String> it = authObj.keys(); it.hasNext(); ) {
            String key = it.next();
            auth.put(key, authObj.optString(key));
        }

        Map<String, List<String>> extraHeaders = null;
        Map<String, String> cookies = applicationSteps.getCookies();
        if (!cookies.isEmpty()) {
            String cookieValue = cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));
            extraHeaders = Map.of("Cookie", List.of(cookieValue));
        }

        client.connect(baseUrl, auth, extraHeaders);
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

    @那么("等待最多 {int} 秒接收 Socket.IO 事件")
    public void waitForEvents(int seconds) throws InterruptedException {
        client.waitForEvents(seconds);
    }

    @那么("收到的 Socket.IO 事件应满足:")
    public void verifyEvents(String dalExpression) {
//        List<Map<String, Object>> events = client.drainEvents();
        for (Map<String, Object> event : client.getReceivedEvents()) {
            Object data = event.get("data");
            if (data != null) {
                extractThreadId(data.toString());
            }
        }
//        if (events.isEmpty()) {
//            throw new AssertionError("Expected Socket.IO events but received none");
//        }
//        try {
//            Assertions.expect(events).should(dalExpression);
//        } catch (AssertionError e) {
//            if (e.getMessage() != null && e.getMessage().contains("Expect a verification operator")) {
//                // DAL doesn't support the operator, but events are non-empty - that's sufficient
//                return;
//            }
//            throw e;
//        }
        expect(client).should(dalExpression);
    }

    @After(order = 999)
    public void disconnect() {
        if (client != null) {
            client.clear();
            client = null;
        }
    }

    private void extractThreadId(String text) {
        var matcher = THREAD_ID_PATTERN.matcher(text);
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
}
