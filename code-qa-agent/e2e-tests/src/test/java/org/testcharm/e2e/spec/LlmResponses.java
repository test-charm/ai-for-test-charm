package org.testcharm.e2e.spec;

import org.testcharm.jfactory.Spec;

public class LlmResponses {

    public static class LlmResponse extends Spec<org.testcharm.e2e.dto.LlmResponse> {
        @Override
        public void main() {
            property("created").defaultValue(1752050400);
        }
    }
}
