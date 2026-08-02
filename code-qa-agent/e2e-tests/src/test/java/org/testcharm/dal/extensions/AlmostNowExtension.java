package org.testcharm.dal.extensions;

import org.testcharm.dal.DAL;
import org.testcharm.dal.runtime.Data;
import org.testcharm.dal.runtime.Extension;
import org.testcharm.dal.runtime.NameStrategy;
import org.testcharm.dal.type.Partial;
import org.testcharm.dal.type.Schema;
import org.testcharm.util.Classes;

import java.time.*;

public class AlmostNowExtension implements Extension {
    @Override
    public void extend(DAL dal) {
        dal.getRuntimeContextBuilder().registerSchema(NameStrategy.SIMPLE_NAME, AlmostNow.class);
        dal.getRuntimeContextBuilder().registerSchema(NameStrategy.SIMPLE_NAME, AlmostNowSH.class);
    }

    @Partial
    public static class AlmostNowSH implements Schema {

        @Override
        public void verify(Data<?> data) {
            if (data.isNull())
                throw new AssertionError("value is null");
            Object instance = data.value();
            if (instance instanceof LocalDateTime)
                compareSh((LocalDateTime) instance);
            else if (instance instanceof String)
                try {
                    compareSh(LocalDateTime.parse((String) instance));
                } catch (Exception ignore) {
                    compare(OffsetDateTime.parse((String) instance).toInstant());
                }
            else
                throw new AssertionError(String.format("unsupported time type: '%s'[%s]",
                        Classes.getClassName(instance), instance));
        }
    }

    @Partial
    public static class AlmostNow implements Schema {

        @Override
        public void verify(Data data) {
            if (data.isNull())
                throw new AssertionError("value is null");
            Object instance = data.instance();
            if (instance instanceof LocalDateTime)
                compare((LocalDateTime) instance);
            else if (instance instanceof String) {
                try {
                    compare(LocalDateTime.parse((String) instance));
                } catch (DateTimeException ignore) {
                    compare(Instant.parse((String) instance));
                }
            } else if (instance instanceof Instant) {
                compare((Instant) instance);
            } else if (instance instanceof Number) {
                long l = ((Number) instance).longValue();
                if (l > 100000000000L)
                    compare(Instant.ofEpochMilli(l));
                else
                    compare(Instant.ofEpochSecond(l));
            } else
                throw new AssertionError(String.format("unsupported time type: '%s'[%s]",
                        Classes.getClassName(instance), instance));
        }
    }

    private static void compareSh(LocalDateTime instance) {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("Asia/Shanghai"));
        long millis = Duration.between(now, instance).abs().toMillis();
        if (millis > 30 * 1000)
            throw new AssertionError(String.format("expecting time is now[%s], but[%s]", now, instance));
    }

    private static void compare(LocalDateTime instance) {
        LocalDateTime now = LocalDateTime.now();
        long millis = Duration.between(now, instance).abs().toMillis();
        if (millis > 30 * 1000)
            throw new AssertionError(String.format("expecting time is now[%s], but[%s]", now, instance));
    }

    private static void compare(Instant instance) {
        Instant now = Instant.now();
        long millis = Duration.between(now, instance).abs().toMillis();
        if (millis > 30 * 1000)
            throw new AssertionError(String.format("expecting time is now[%s], but[%s]", now, instance));
    }
}
