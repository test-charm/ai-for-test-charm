package com.testcharm;

import com.github.leeonky.jfactory.*;
import com.github.leeonky.util.Classes;
import com.testcharm.entity.FeatureFile;
import lombok.SneakyThrows;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

@Configuration
public class Factories {

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
    public JFactory factorySet(TempFiles tempFiles, DALMockServer dalMockServer) {
        var jFactory = new JFactory(new CompositeDataRepository(new MemoryDataRepository())
                .registerByType(FeatureFile.class, new FeatureFileDataRepository(tempFiles))
                .registerByType(HttpRequest.class, new MockServerDataRepository(dalMockServer)));
        Classes.subTypesOf(Spec.class, "com.testcharm.spec").forEach(c -> jFactory.register((Class) c));
        return jFactory;
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
