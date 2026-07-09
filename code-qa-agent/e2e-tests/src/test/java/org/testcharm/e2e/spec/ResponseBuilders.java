package org.testcharm.e2e.spec;

import org.testcharm.e2e.DALMockServer;
import org.testcharm.jfactory.Spec;

public class ResponseBuilders {

    public static class DefaultResponseBuilder extends Spec<DALMockServer.ResponseBuilder> {
        @Override
        public void main() {
            property("code").value(200);
            property("times").value(0);
            property("delayResponse").value(0);
        }
    }
}
