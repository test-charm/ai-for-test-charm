package org.testcharm.e2e.spec;

import com.github.leeonky.jfactory.Spec;
import org.testcharm.e2e.DALMockServer;

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
