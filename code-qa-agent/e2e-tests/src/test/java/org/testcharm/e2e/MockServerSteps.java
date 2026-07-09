package org.testcharm.e2e;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.zh_cn.假如;
import io.cucumber.java.zh_cn.并且;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcharm.dal.Assertions;
import org.testcharm.jfactory.JFactory;

import java.util.Map;
import java.util.stream.IntStream;

public class MockServerSteps {

    @Autowired
    private MockServerClient mockServerClient;

    @Autowired
    private DALMockServer dalMockServer;

    @Autowired
    private JFactory jFactory;

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
    public void mockApi(String mock) {
        String[] requestAndResponses = mock.split("---");
        var responseBuilders = IntStream.range(1, requestAndResponses.length)
                .mapToObj(i -> (DALMockServer.ResponseBuilder)
                        jFactory.useDAL().create("DefaultResponseBuilder", requestAndResponses[i].trim()))
                .toList();
        dalMockServer.mock(Map.of(requestAndResponses[0].trim(), responseBuilders));
    }

    @并且("验证Mock API:")
    public void verifyMockApi(String dalExpression) {
        Assertions.expect(dalMockServer.requests()).should(dalExpression);
    }
}
