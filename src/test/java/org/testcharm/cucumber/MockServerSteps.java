package org.testcharm.cucumber;

import com.github.leeonky.jfactory.cucumber.JData;
import com.github.leeonky.jfactory.cucumber.Table;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.zh_cn.假如;
import io.cucumber.java.zh_cn.并且;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.stream.IntStream;

import static com.github.leeonky.dal.Assertions.expect;

public class MockServerSteps {

    static {
        ClientAndServer.startClientAndServer(1080);
    }

    @Autowired
    private MockServerClient mockServerClient;
    @Autowired
    private DALMockServer dalMockServer;
    @Autowired
    private JData jData;

    @Before(order = 0)
    public void setupMockServer() {
        mockServerClient.reset();
        dalMockServer.clear();
    }

    @After(order = 0)
    public void tearDownMockServer() {
        dalMockServer.stopDelay();
    }

    @假如("Mock API:")
    public void mock_api(String mock) {
        String[] requestAndResponses = mock.split("---");

        var responseBuilders = IntStream.range(1, requestAndResponses.length)
                .mapToObj(i -> (DALMockServer.ResponseBuilder)
                        jData.prepare("DefaultResponseBuilder", Table.create(requestAndResponses[i].trim())).get(0))
                .toList();

        dalMockServer.mock(Map.of(requestAndResponses[0].trim(), responseBuilders));
    }

    @并且("验证Mock API:")
    public void 验证mockAPI(String dalExpression) {
        expect(dalMockServer.requests()).should(dalExpression);
    }
}
