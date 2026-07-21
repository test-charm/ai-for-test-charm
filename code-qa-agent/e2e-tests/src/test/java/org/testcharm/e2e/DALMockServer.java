package org.testcharm.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcharm.dal.DAL;
import org.testcharm.interpreter.InterpreterException;
import org.testcharm.interpreter.SyntaxException;
import lombok.SneakyThrows;
import org.mockserver.client.MockServerClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.mockserver.serialization.HttpRequestSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.mockserver.model.HttpRequest.request;

@Component
public class DALMockServer {

    private final Map<String, List<ResponseBuilder>> allExpectations = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, AtomicInteger> responseTimes = Collections.synchronizedMap(new HashMap<>());
    private final Queue<HttpRequest> allRequests = new ConcurrentLinkedDeque<>();
    private final MockServerLogger mockServerLogger = new MockServerLogger(DALMockServer.class);
    private final HttpRequestSerializer requestSerializer = new HttpRequestSerializer(mockServerLogger);
    private final Object lock = new Object();
    private boolean stopDelay = true;

    @Autowired
    private MockServerClient mockServerClient;

    public void clear() {
        stopDelay = true;
        allExpectations.clear();
        allRequests.clear();
        responseTimes.clear();
    }

    @SneakyThrows
    public void stopDelay() {
        if (stopDelay) {
            return;
        }
        stopDelay = true;
        TimeUnit.SECONDS.sleep(2);
    }

    public List<HttpRequest> requests() {
        return new ArrayList<>(allRequests);
    }

    public void mock(Map<String, List<ResponseBuilder>> expectationResponses) {
        allExpectations.putAll(expectationResponses);
        for (String expectation : expectationResponses.keySet()) {
            responseTimes.put(expectation, new AtomicInteger(0));
        }
        mockServerClient.when(request()).respond(httpRequest -> {
            synchronized (lock) {
                allRequests.add(httpRequest);
                List<Unexpectation> unexpectations = new ArrayList<>();
                List<Map.Entry<String, List<ResponseBuilder>>> entries = new ArrayList<>(allExpectations.entrySet());
                Collections.reverse(entries);
                for (Map.Entry<String, List<ResponseBuilder>> entry : entries) {
                    String expectation = entry.getKey();
                    List<ResponseBuilder> responseBuilders = entry.getValue();
                    if (responseBuilders.size() == 1) {
                        ResponseBuilder response = responseBuilders.get(0);
                        try {
                            DAL.dal("MockD").evaluate(httpRequest, expectation);
                            if (response.times > 0) {
                                int currentTime = responseTimes.get(expectation).incrementAndGet();
                                if (currentTime > response.times) {
                                    unexpectations.add(new Unexpectation(
                                            expectation,
                                            String.format("times %s more than %s", currentTime, response.times)
                                    ));
                                    continue;
                                }
                            }
                            if (response.delayResponse > 0) {
                                delay(response);
                            }
                            HttpResponse httpResponse = HttpResponse.response().withStatusCode(response.code)
                                    .withContentType(MediaType.APPLICATION_JSON);
                            response.giveBody(httpResponse);
                            response.buildHeaders().forEach(httpResponse::withHeader);
                            return httpResponse;
                        } catch (SyntaxException e) {
                            return HttpResponse.response()
                                    .withStatusCode(500)
                                    .withBody(e.getMessage() + "\n\n" + e.show(expectation));
                        } catch (InterpreterException ex) {
                            unexpectations.add(new Unexpectation(expectation, ex));
                        }
                    } else {
                        List<ResponseBuilder> expanded = responseBuilders.stream().flatMap(
                                responseBuilder -> IntStream.range(0, responseBuilder.times == 0 ? 1 : responseBuilder.times)
                                        .mapToObj(i -> responseBuilder)
                        ).toList();
                        try {
                            DAL.dal("MockD").evaluate(httpRequest, expectation);
                            int currentTime = responseTimes.get(expectation).getAndIncrement();
                            if (currentTime >= expanded.size()) {
                                unexpectations.add(new Unexpectation(
                                        expectation,
                                        String.format("times %s more than %s", currentTime, expanded.size())
                                ));
                            } else {
                                ResponseBuilder response = expanded.get(currentTime);
                                if (response.delayResponse > 0) {
                                    delay(response);
                                }
                                HttpResponse httpResponse = HttpResponse.response().withStatusCode(response.code)
                                        .withContentType(MediaType.APPLICATION_JSON);
                                response.giveBody(httpResponse);
                                response.buildHeaders().forEach(httpResponse::withHeader);
                                return httpResponse;
                            }
                        } catch (SyntaxException e) {
                            return HttpResponse.response()
                                    .withStatusCode(500)
                                    .withBody(e.getMessage() + "\n\n" + e.show(expectation));
                        } catch (InterpreterException ex) {
                            unexpectations.add(new Unexpectation(expectation, ex));
                        }
                    }
                }
                return HttpResponse.response(buildMessage(httpRequest, unexpectations)).withStatusCode(404);
            }
        });
    }

    private String buildMessage(HttpRequest httpRequest, List<Unexpectation> unexpectations) {
        StringBuilder message = new StringBuilder();
        message.append("Unexpected request:\n")
                .append(requestSerializer.serialize(httpRequest))
                .append("\n")
                .append("Expectations:");
        unexpectations.stream().map(Unexpectation::message).forEach(message::append);
        return message.toString();
    }

    @SneakyThrows
    private void delay(ResponseBuilder response) {
        stopDelay = false;
        for (int i = 0; i < response.delayResponse; i++) {
            if (stopDelay) {
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
    }

    public static final class Unexpectation {
        public final String expectation;
        private final String message;

        public Unexpectation(String expectation, InterpreterException ex) {
            this(expectation, "\n----------------------------------------------------------\n" + ex.show(expectation) + "\n" + ex.getMessage());
        }

        public Unexpectation(String expectation, String message) {
            this.expectation = expectation;
            this.message = "expectation\n" + message;
        }

        public String message() {
            return message;
        }
    }

    public static class ResponseBuilder {
        public int code;
        public Object body;
        public int times;
        public int delayResponse;
        public Map<String, Object> headers = new LinkedHashMap<>();

        public List<Header> buildHeaders() {
            return headers.entrySet().stream().map(entry -> {
                if (entry.getValue() instanceof List<?> values) {
                    return new Header(entry.getKey(), values.stream().map(String::valueOf).toArray(String[]::new));
                }
                return new Header(entry.getKey(), String.valueOf(entry.getValue()));
            }).toList();
        }

        @SneakyThrows
        @SuppressWarnings("unchecked")
        public void giveBody(HttpResponse httpResponse) {
            if (body instanceof String string) {
                httpResponse.withBody(string);
            } else if (body instanceof List<?> bytes) {
                byte[] binary = new byte[bytes.size()];
                for (int i = 0; i < bytes.size(); i++) {
                    binary[i] = (Byte) bytes.get(i);
                }
                httpResponse.withBody(binary);
            } else {
                httpResponse.withBody(new ObjectMapper().writeValueAsString(body));
            }
        }
    }
}
