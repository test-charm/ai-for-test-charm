package org.testcharm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TestLogAppender extends AppenderBase<ILoggingEvent> {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLoggerName().startsWith("org.testcharm.DifyKbUploader")) {
            try {
                restTemplate.exchange(RequestEntity.post("http://mock-server.tool.net:1080/e2e/logger").contentType(MediaType.APPLICATION_JSON).body(new ILoggingEvent() {
                    @Override
                    public String getThreadName() {
                        return null;
                    }

                    @Override
                    public Level getLevel() {
                        return event.getLevel();
                    }

                    @Override
                    public String getMessage() {
                        return event.getFormattedMessage();
                    }

                    @Override
                    public Object[] getArgumentArray() {
                        return new Object[0];
                    }

                    @Override
                    public String getFormattedMessage() {
                        return null;
                    }

                    @Override
                    public String getLoggerName() {
                        return null;
                    }

                    @Override
                    public LoggerContextVO getLoggerContextVO() {
                        return null;
                    }

                    @Override
                    public IThrowableProxy getThrowableProxy() {
                        return null;
                    }

                    @Override
                    public StackTraceElement[] getCallerData() {
                        return new StackTraceElement[0];
                    }

                    @Override
                    public boolean hasCallerData() {
                        return false;
                    }

                    @Override
                    public Marker getMarker() {
                        return null;
                    }

                    @Override
                    public List<Marker> getMarkerList() {
                        return List.of();
                    }

                    @Override
                    public Map<String, String> getMDCPropertyMap() {
                        return null;
                    }

                    @Override
                    public Map<String, String> getMdc() {
                        return null;
                    }

                    @Override
                    public long getTimeStamp() {
                        return Instant.now().toEpochMilli();
                    }

                    @Override
                    public int getNanoseconds() {
                        return 0;
                    }

                    @Override
                    public long getSequenceNumber() {
                        return 0;
                    }

                    @Override
                    public List<KeyValuePair> getKeyValuePairs() {
                        return List.of();
                    }

                    @Override
                    public void prepareForDeferredProcessing() {

                    }
                }), String.class);
            } catch (Exception sendLoggerEventException) {
                System.out.println("sendLoggerEventException = " + sendLoggerEventException.getMessage());
            }
        }
    }

}
