package org.testcharm.e2e;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;

import java.util.Map;

/**
 * No-op {@link JsonSchemaValidator} that accepts all schemas and content as valid.
 * Needed because json-schema-validator is excluded from mcp-json-jackson2 to avoid
 * version conflicts with MockServer 5.15.0 (which requires 1.0.76 but MCP SDK needs 2.0.0).
 */
public class NoOpJsonSchemaValidator implements JsonSchemaValidator {

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        return ValidationResponse.asValid(null);
    }

    public static class Supplier implements JsonSchemaValidatorSupplier {
        private static final NoOpJsonSchemaValidator INSTANCE = new NoOpJsonSchemaValidator();

        @Override
        public JsonSchemaValidator get() {
            return INSTANCE;
        }
    }
}
