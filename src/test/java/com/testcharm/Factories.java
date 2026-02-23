package com.testcharm;

import com.github.leeonky.jfactory.*;
import com.github.leeonky.util.Classes;
import com.testcharm.entity.FeatureFile;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.List;

@Configuration
public class Factories {

    @Bean
    public JFactory factorySet(TempFiles tempFiles) {
        var jFactory = new JFactory(new CompositeDataRepository(new MemoryDataRepository()).registerByType(FeatureFile.class, new FeatureFileDataRepository(tempFiles)));
        Classes.subTypesOf(Spec.class, "com.testcharm.spec").forEach(c -> jFactory.register((Class) c));
        return jFactory;
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
                tempFiles.createWithContent("input/test-charm.feature", featureFile.getContent().replaceAll("'''", "\"\"\""));
            }
        }
    }
}
