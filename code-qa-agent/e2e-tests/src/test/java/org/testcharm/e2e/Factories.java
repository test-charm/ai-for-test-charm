package org.testcharm.e2e;

import org.testcharm.jfactory.CompositeDataRepository;
import org.testcharm.jfactory.DataRepository;
import org.testcharm.jfactory.JFactory;
import org.testcharm.jfactory.MemoryDataRepository;
import org.testcharm.jfactory.Spec;
import org.testcharm.util.Classes;
import lombok.SneakyThrows;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;
import java.util.Collection;

@Configuration
public class Factories {

    @SneakyThrows
    @Bean
    public MockServerClient createMockServerClient(@Value("${mock-server.endpoint}") String endpoint) {
        URL url = new URL(endpoint);
        return new MockServerClient(url.getHost(), url.getPort()) {
            @Override
            public void close() {
            }
        };
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public JFactory factorySet(DALMockServer dalMockServer) {
        var jFactory = new JFactory(
                new CompositeDataRepository(new MemoryDataRepository())
                        .registerByType(HttpRequest.class, new MockServerDataRepository(dalMockServer))
        );
        Classes.subTypesOf(Spec.class, "org.testcharm.e2e.spec")
                .forEach(spec -> jFactory.register((Class) spec));
        return jFactory;
    }

    public static class MockServerDataRepository implements DataRepository {
        private final DALMockServer dalMockServer;

        public MockServerDataRepository(DALMockServer dalMockServer) {
            this.dalMockServer = dalMockServer;
        }

        @Override
        @SuppressWarnings("unchecked")
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
}
