package org.testcharm.e2e;

import lombok.Getter;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Socket.IO client using HTTP long-polling transport.
 * Implements just enough Socket.IO protocol for Chainlit e2e tests.
 */
public class SocketIOClient {

    @Getter
    private final List<Map<String, Object>> receivedEvents = new CopyOnWriteArrayList<>();
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connected;
    private volatile boolean running;
    private String engineSid;
    private String baseHttpUrl;
    private Map<String, List<String>> extraHeaders;
    private int pollSeq;
    private volatile Thread pollThread;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public void connect(String baseUrl, Map<String, String> auth, Map<String, List<String>> extraHeaders) throws Exception {
        this.baseHttpUrl = baseUrl + "/ws/socket.io/?EIO=4&transport=polling";

        // Login and capture session cookie if not provided
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        // If no cookie provided, login to get one
        if (!headers.containsKey("Cookie") && !headers.containsKey("cookie")) {
            String cookieValue = loginAndGetCookie(baseUrl);
            if (cookieValue != null && !cookieValue.isEmpty()) {
                headers.put("Cookie", List.of(cookieValue));
            }
        }
        this.extraHeaders = headers;

        // Step 1: Engine.IO handshake
        String handshakeResp = httpGet(baseHttpUrl);
        if (handshakeResp == null || handshakeResp.isEmpty()) {
            throw new RuntimeException("Empty handshake response");
        }
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
        String connectUrl = baseHttpUrl + "&sid=" + engineSid;
        JSONObject authJson = new JSONObject(auth);
        String connectBody = "40" + authJson;
        String connectResp = httpPost(connectUrl, connectBody);
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
                String pollUrl = baseHttpUrl + "&sid=" + engineSid + "&t=" + (pollSeq++);
                String resp = httpGet(pollUrl);
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

    private String loginAndGetCookie(String baseUrl) throws IOException {
        String loginUrl = baseUrl + "/login";
        Request request = new Request.Builder()
                .url(loginUrl)
                .post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), "username=joseph&password=joseph"))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            List<String> cookies = response.headers("Set-Cookie");
            if (cookies.isEmpty()) {
                cookies = response.headers("set-cookie");
            }
            for (String cookie : cookies) {
                String[] parts = cookie.split(";", 2);
                if (parts[0].startsWith("access_token=")) {
                    return parts[0];
                }
            }
        }
        return null;
    }

    private String httpGet(String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        addHeaders(builder);
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    private String httpPost(String url, String body) throws IOException {
        Request.Builder builder = new Request.Builder().url(url)
                .post(RequestBody.create(MediaType.parse("text/plain"), body));
        addHeaders(builder);
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    private void addHeaders(Request.Builder builder) {
        if (extraHeaders != null) {
            for (Map.Entry<String, List<String>> entry : extraHeaders.entrySet()) {
                for (String value : entry.getValue()) {
                    builder.addHeader(entry.getKey(), value);
                }
            }
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
                    httpPost(baseHttpUrl + "&sid=" + engineSid, "3");
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
        try {
            String postUrl = baseHttpUrl + "&sid=" + engineSid;
            httpPost(postUrl, "42" + arr);
        } catch (IOException e) {
            throw new RuntimeException("Failed to emit event: " + event, e);
        }
    }

    public void waitForEvents(int timeoutSeconds) throws InterruptedException {
        Thread.sleep(timeoutSeconds * 1000L);
    }

//    public List<Map<String, Object>> drainEvents() {
//        running = false;
//        if (pollThread != null) {
//            pollThread.interrupt();
//        }
//        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
//        List<Map<String, Object>> events = new ArrayList<>();
//        Map<String, Object> event;
//        while ((event = receivedEvents.poll()) != null) {
//            events.add(event);
//        }
//        return events;
//    }

    public void clear() {
        running = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        receivedEvents.clear();
        connected = false;
    }
}
