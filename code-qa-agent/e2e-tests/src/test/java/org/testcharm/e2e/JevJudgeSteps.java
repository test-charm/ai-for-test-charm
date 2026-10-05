package org.testcharm.e2e;

import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.testcharm.cucumber.restful.RestfulStep;
import org.testcharm.jfactory.JFactory;

import static org.testcharm.dal.Assertions.expect;

public class JevJudgeSteps {

    @Value("${jev-judge.base-url:http://localhost:18003}")
    private String judgeBaseUrl;

    private Object judgeResponse;

    @SneakyThrows
    @当("向评测服务发送请求:")
    public void 向评测服务发送请求(String requestJson) {
        var restfulStep = new RestfulStep();
        restfulStep.setJFactory(new JFactory());
        restfulStep.setBaseUrl(judgeBaseUrl);
        restfulStep.postInJson("/containment", requestJson.trim());
        judgeResponse = restfulStep.response("body.json");
    }

    @那么("评测结果应满足:")
    public void 评测结果应满足(String dalExpression) {
        expect(judgeResponse).should(dalExpression);
    }
}
