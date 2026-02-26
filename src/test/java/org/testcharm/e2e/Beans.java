package org.testcharm.e2e;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.leeonky.jfactory.*;
import com.github.leeonky.util.Classes;
import lombok.SneakyThrows;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcharm.e2e.entity.FeatureFile;
import org.testcharm.e2e.entity.WaitingTime;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.mockserver.model.HttpRequest.request;

@Configuration
public class Beans {

    @Bean
    @ConditionalOnMissingBean({MockServerClient.class})
    public MockServerClient mockServerClient(@Value("${mock-server.endpoint}") String endpoint) throws MalformedURLException {
        URL url = new URL(endpoint);
        return new MockServerClient(url.getHost(), url.getPort()) {

            @Override
            public void close() {
            }
        };
    }

    @Bean
    public JFactory factorySet(TempFiles tempFiles, DALMockServer dalMockServer, MockServerClient mockServerClient) {
        var jFactory = new JFactory(new CompositeDataRepository(new MemoryDataRepository())
                .registerByType(FeatureFile.class, new FeatureFileDataRepository(tempFiles))
                .registerByType(HttpRequest.class, new MockServerDataRepository(dalMockServer))
                .registerByType(LoggingEvent.class, new LoggingEventDataRepository(mockServerClient))
                .registerByType(WaitingTime.class, new WaitingTimeDataRepository(mockServerClient)));
        Classes.subTypesOf(Spec.class, "org.testcharm.e2e.spec").forEach(c -> jFactory.register((Class) c));
        return jFactory;
    }

    public static class WaitingTimeDataRepository implements DataRepository {
        private final MockServerClient mockServerClient;

        public WaitingTimeDataRepository(MockServerClient mockServerClient) {
            this.mockServerClient = mockServerClient;
        }

        @Override
        public <T> Collection<T> queryAll(Class<T> type) {
            return (Collection<T>) Arrays.stream(mockServerClient.retrieveRecordedRequests(request().withPath("/mock/sleep-seconds")))
                    .map(this::requestAsWaitingTime).toList();
        }

        @Override
        public void clear() {

        }

        @Override
        public void save(Object object) {

        }

        @SneakyThrows
        private WaitingTime requestAsWaitingTime(HttpRequest httpRequest) {
            return new WaitingTime().setSeconds(Long.parseLong(httpRequest.getFirstQueryStringParameter("seconds")));
        }

    }

    public static class LoggingEventDataRepository implements DataRepository {
        private final MockServerClient mockServerClient;

        public LoggingEventDataRepository(MockServerClient mockServerClient) {
            this.mockServerClient = mockServerClient;
        }

        @Override
        public <T> Collection<T> queryAll(Class<T> type) {
            return (Collection<T>) Arrays.stream(mockServerClient.retrieveRecordedRequests(request().withPath("/e2e/logger")))
                    .map(this::requestAsEvent).toList();
        }

        @SneakyThrows
        private LoggingEvent requestAsEvent(HttpRequest httpRequest) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new SimpleModule().addDeserializer(Level.class, new LevelDeserializer()));
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            @JsonIgnoreProperties({"instant"})
            class IgnoreInstantMixIn {
            }
            objectMapper.addMixIn(LoggingEvent.class, IgnoreInstantMixIn.class);

            return objectMapper.readValue(httpRequest.getBodyAsString(), LoggingEvent.class);
        }

        @Override
        public void clear() {

        }

        @Override
        public void save(Object object) {

        }

        public static class LevelDeserializer extends StdDeserializer<Level> {

            protected LevelDeserializer() {
                super(Level.class);
            }

            @Override
            public Level deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
                return Level.toLevel(jp.getCodec().<JsonNode>readTree(jp).get("levelInt").asInt());
            }
        }
    }

    public static class MockServerDataRepository implements DataRepository {

        private final DALMockServer dalMockServer;

        public MockServerDataRepository(DALMockServer dalMockServer) {
            this.dalMockServer = dalMockServer;
        }

        @Override
        public <T> Collection<T> queryAll(Class<T> type) {
            return (Collection<T>) dalMockServer.requests();
        }

        @Override
        public void clear() {

        }

        @Override
        public void save(Object object) {

        }
    }

    public static class FeatureFileDataRepository implements DataRepository {

        private final TempFiles tempFiles;

        public FeatureFileDataRepository(TempFiles tempFiles) {
            this.tempFiles = tempFiles;
        }

        @Override
        public <T> Collection<T> queryAll(Class<T> type) {
            return List.of();
        }

        @Override
        public void clear() {

        }

        @SneakyThrows
        @Override
        public void save(Object object) {
            if (object instanceof FeatureFile featureFile) {
                tempFiles.createWithContent("input/" + featureFile.getFileName(), featureFile.getContent().replaceAll("'''", "\"\"\""));
            }
        }
    }
}
