package org.testcharm.e2e;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.testcharm.cucumber.restful.RestfulStep;
import org.testcharm.jfactory.JFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class SocketIOClient {

    @Getter
    private final List<Map<String, Object>> receivedEvents = new CopyOnWriteArrayList<>();
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connected;
    private volatile boolean running;
    private String engineSid;
    private int pollSeq;
    private volatile Thread pollThread;
    private volatile int connectionGeneration;
    private final String wsBasePath = "/ws/socket.io/?EIO=4&transport=polling";
    private final AtomicInteger pollCount = new AtomicInteger(0);
    private final AtomicInteger pollErrorCount = new AtomicInteger(0);

    @Autowired
    @Lazy
    private RestfulStep restfulStep;

    @Autowired
    @Lazy
    private JFactory jFactory;

    @Value("${app.base-url}")
    private String baseUrl;

    /** Dedicated RestfulStep for the poll thread to avoid lock contention with the test thread. */
    private volatile RestfulStep pollStep;

    public void connect(Map<String, String> auth) throws Exception {
        stopPollThread(true);

        // Step 1: Engine.IO handshake
        String handshakeResp = httpGet(restfulStep, wsBasePath);

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
        String connectResp = httpPost(restfulStep, wsBasePath + "&sid=" + engineSid, connectBody);
        if (!"OK".equals(connectResp)) {
            throw new RuntimeException("CONNECT failed: " + connectResp);
        }

        // Step 3: Start polling for events
        pollStep = new RestfulStep();
        pollStep.setBaseUrl(baseUrl);
        pollStep.setJFactory(jFactory);
        connected = true;
        connectedLatch.countDown();
        running = true;
        pollSeq = 0;
        int myGeneration = ++connectionGeneration;
        pollThread = new Thread(() -> pollLoop(myGeneration));
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private void pollLoop(int generation) {
        log.info("Poll loop started generation={}", generation);
        int consecutiveErrors = 0;
        while (running && generation == connectionGeneration) {
            int count = pollCount.incrementAndGet();
            try {
                if (count <= 3 || count % 10 == 0) {
                    log.info("Poll #{} starting GET sid={} running={}", count, engineSid, running);
                }
                String resp = httpGet(pollStep, wsBasePath + "&sid=" + engineSid + "&t=" + (pollSeq++));
                if (resp != null && !resp.isEmpty()) {
                    // Handle Engine.IO ping: a lone "2" character
                    if (resp.equals("2")) {
                        try {
                            httpPost(pollStep, wsBasePath + "&sid=" + engineSid, "3");
                        } catch (Exception ignored) {}
                        if (running && generation == connectionGeneration) {
                            Thread.sleep(50);
                        }
                        continue;
                    }
                    // Detect invalid session to break tight loop
                    if (resp.contains("Invalid session")) {
                        log.error("Poll #{} received 'Invalid session', stopping poll loop", count);
                        receivedEvents.add(Map.of("name", "invalid_session",
                                "data", Map.of("message", resp)));
                        break;
                    }
                    log.info("Poll #{} received {} chars, preview: {}", count, resp.length(),
                            resp.length() > 120 ? resp.substring(0, 120) + "..." : resp);
                    processMessages(resp);
                    log.info("Poll #{} after processMessages, total events={}, event names: {}",
                            count, receivedEvents.size(),
                            receivedEvents.stream().map(e -> e.get("name")).toList());
                } else {
                    log.debug("Poll #{} received empty response", count);
                }
                // Reset consecutive errors on successful poll
                consecutiveErrors = 0;
                // Prevent tight-looping: always yield between polls
                if (running && generation == connectionGeneration) {
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                log.info("Poll loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                int errCount = pollErrorCount.incrementAndGet();
                consecutiveErrors++;
                log.error("Poll #{} error (total errors={}, consecutive={}): {}",
                        count, errCount, consecutiveErrors, e.toString());
                if (running && generation == connectionGeneration) {
                    receivedEvents.add(Map.of("name", "poll_error",
                            "data", Map.of("message", e.getMessage())));
                    // Keep polling — never give up. The test will eventually time out
                    // if the session is truly broken, and diagnostic logs will show the cause.
                    if (consecutiveErrors > 0 && consecutiveErrors % 10 == 0) {
                        log.warn("Poll loop has {} consecutive errors, still retrying", consecutiveErrors);
                    }
                    try { Thread.sleep(500); } catch (InterruptedException ignored) { break; }
                }
            }
        }
        log.info("Poll loop exiting generation={} running={} currentGeneration={}",
                generation, running, connectionGeneration);
    }

    // ── HTTP helpers (thread-safe: caller passes the RestfulStep to use) ──

    private String httpGet(RestfulStep step, String path) {
        step.get(path);
        return step.response("body.string");
    }

    private String httpPost(RestfulStep step, String path, String body) {
        step.post(path, "text/plain", body);
        return step.response("body.string");
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
                    httpPost(pollStep, wsBasePath + "&sid=" + engineSid, "3");
                } catch (Exception ignored) {}
            } else if (engineType == '3') {
                // Server pong — no action needed
                log.debug("Received Engine.IO pong");
            } else if (engineType == '1') {
                // Server closed the transport
                log.warn("Received Engine.IO close packet, session may be invalid");
                receivedEvents.add(Map.of("name", "engine_close",
                        "data", Map.of("message", "Engine.IO close packet received")));
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
        Exception lastEx = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                httpPost(restfulStep, wsBasePath + "&sid=" + engineSid, "42" + arr);
                return;
            } catch (Exception e) {
                lastEx = e;
                if (attempt < 2) {
                    try { Thread.sleep(200L * (attempt + 1)); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new RuntimeException("Failed to emit event: " + event, lastEx);
    }

    public void clear() {
        stopPollThread(false);
        receivedEvents.clear();
        connected = false;
    }

    private void stopPollThread(boolean wait) {
        running = false;
        connectionGeneration++;
        Thread old = pollThread;
        pollThread = null;
        if (old != null) {
            old.interrupt();
            if (wait) {
                try {
                    old.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

}
