package org.testcharm.e2e;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.testcharm.cucumber.restful.RestfulStep;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

@Component
public class SocketIOClient {

    @Getter
    private final List<Map<String, Object>> receivedEvents = new CopyOnWriteArrayList<>();
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connected;
    private volatile boolean running;
    private String engineSid;
    private int pollSeq;
    private volatile Thread pollThread;
    private final String wsBasePath = "/ws/socket.io/?EIO=4&transport=polling";

    @Autowired
    @Lazy
    private RestfulStep restfulStep;

    public void connect(Map<String, String> auth) throws Exception {
        resolveScopedProxy();

        // Step 1: Engine.IO handshake
        String handshakeResp = httpGet(wsBasePath);

        // Parse sid from "0{...}"
        if (handshakeResp.startsWith("0")) {
            String handshakeData = handshakeResp.substring(1);
            JSONObject hso = new JSONObject(handshakeData);
            this.engineSid = hso.optString("sid");
        }
        if (engineSid == null || engineSid.isEmpty()) {
            throw new RuntimeException("No sid in handshake: " + handshakeResp);
        }

        // Step 2: Send CONNECT packet
        JSONObject authJson = new JSONObject(auth);
        String connectBody = "40" + authJson;
        String connectResp = httpPost(wsBasePath + "&sid=" + engineSid, connectBody);
        if (!"OK".equals(connectResp)) {
            throw new RuntimeException("CONNECT failed: " + connectResp);
        }

        // Step 3: Start polling for events
        connected = true;
        connectedLatch.countDown();
        running = true;
        pollSeq = 0;
        pollThread = new Thread(this::pollLoop);
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollLoop() {
        while (running) {
            try {
                String resp = httpGet(wsBasePath + "&sid=" + engineSid + "&t=" + (pollSeq++));
                if (resp != null && resp.length() > 1) {
                    processMessages(resp);
                }
            } catch (Exception e) {
                if (running) {
                    receivedEvents.add(Map.of("name", "poll_error",
                            "data", Map.of("message", e.getMessage())));
                }
            }
        }
    }

    /**
     * Force Spring to resolve the cucumber-glue scoped proxy on the current
     * (Cucumber) thread, then unwrap it so the poll thread can use the actual
     * RestfulStep instance without needing the scope to be active.
     */
    private void resolveScopedProxy() {
        if (restfulStep instanceof Advised advised) {
            try {
                restfulStep = (RestfulStep) advised.getTargetSource().getTarget();
            } catch (Exception e) {
                throw new RuntimeException("Failed to resolve RestfulStep scoped proxy", e);
            }
        }
    }

    private final Object httpLock = new Object();

    private String httpGet(String path) {
        synchronized (httpLock) {
            restfulStep.get(path);
            return restfulStep.response("body.string");
        }
    }

    private String httpPost(String path, String body) throws IOException {
        synchronized (httpLock) {
            restfulStep.post(path, "text/plain", body);
            return restfulStep.response("body.string");
        }
    }

    private void processMessages(String text) {
        // Engine.IO packets can be concatenated: "42[...]42[...]2"
        int pos = 0;
        while (pos < text.length()) {
            char engineType = text.charAt(pos);
            pos++;
            if (engineType == '4' && pos < text.length()) {
                char socketType = text.charAt(pos);
                pos++;
                // Find the JSON payload boundary via bracket counting
                int depth = 0;
                boolean inString = false;
                int start = pos;
                while (pos < text.length()) {
                    char c = text.charAt(pos);
                    pos++;
                    if (inString) {
                        if (c == '"') inString = false;
                    } else if (c == '"') {
                        inString = true;
                    } else if (c == '[' || c == '{') {
                        depth++;
                    } else if (c == ']' || c == '}') {
                        depth--;
                        if (depth == 0) break;
                    }
                }
                String payload = text.substring(start, pos);
                handleSocketMessage(socketType, payload);
            } else if (engineType == '2') {
                try {
                    httpPost(wsBasePath + "&sid=" + engineSid, "3");
                } catch (IOException ignored) {}
            }
        }
    }

    private void handleSocketMessage(char type, String payload) {
        switch (type) {
            case '0': // CONNECT_ACK
                break;
            case '4': // CONNECT_ERROR
                try {
                    JSONObject error = new JSONObject(payload);
                    receivedEvents.add(Map.of("name", "connect_error", "data", toMap(error)));
                } catch (Exception e) {
                    receivedEvents.add(Map.of("name", "connect_error", "data", payload));
                }
                break;
            case '2': // EVENT
                try {
                    JSONArray arr = new JSONArray(payload);
                    String eventName = arr.optString(0);
                    Object eventData = arr.length() > 1 ? unwrap(arr.opt(1)) : null;
                    receivedEvents.add(Map.of("name", eventName, "data", eventData));
                } catch (Exception e) {
                    receivedEvents.add(Map.of("name", "raw_event", "data", payload));
                }
                break;
        }
    }

    private static Map<String, Object> toMap(JSONObject obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String key = it.next();
            map.put(key, unwrap(obj.opt(key)));
        }
        return map;
    }

    private static Object unwrap(Object value) {
        if (value instanceof JSONObject) return toMap((JSONObject) value);
        if (value instanceof JSONArray) {
            List<Object> list = new ArrayList<>();
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) {
                list.add(unwrap(arr.opt(i)));
            }
            return list;
        }
        return value;
    }

    public void emit(String event, Object... data) {
        if (!connected) {
            throw new IllegalStateException("Socket.IO is not connected");
        }
        JSONArray arr = new JSONArray();
        arr.put(event);
        if (data != null && data.length > 0) {
            for (Object item : data) {
                arr.put(item);
            }
        }
        IOException lastEx = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                httpPost(wsBasePath + "&sid=" + engineSid, "42" + arr);
                return;
            } catch (IOException e) {
                lastEx = e;
                if (attempt < 2) {
                    try { Thread.sleep(200L * (attempt + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new RuntimeException("Failed to emit event: " + event, lastEx);
    }

    public void clear() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        receivedEvents.clear();
        connected = false;
    }
}
