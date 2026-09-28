package cn.org.openbanking.dcc.generator.openapi;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;

import org.springframework.stereotype.Component;

/**
 * Generates an OpenAPI 3.0 document (JSON) for a set of business interfaces: one
 * {@code POST} path each, request/response schemas in {@code components}, and an
 * {@code x-mock} block with example payloads so the document supports mocking.
 */
@Component
public class OpenApiGenerator {

    public String generate(String title, String version, List<InterfaceSpec> specs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"openapi\": \"3.0.3\",\n");
        sb.append("  \"info\": { \"title\": ").append(q(title)).append(", \"version\": ").append(q(version))
                .append(" },\n");
        sb.append("  \"paths\": {\n");
        for (int i = 0; i < specs.size(); i++) {
            sb.append(path(specs.get(i), i < specs.size() - 1));
        }
        sb.append("  },\n");

        List<String> schemaDefs = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            schemaDefs.add(schema(spec.interfaceNo() + "Request", spec.content().input()));
            schemaDefs.add(schema(spec.interfaceNo() + "Response", spec.content().output()));
        }
        sb.append("  \"components\": { \"schemas\": {\n    ")
                .append(String.join(",\n    ", schemaDefs))
                .append("\n  } },\n");

        List<String> mocks = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            mocks.add("    " + q(spec.interfaceNo()) + ": { \"request\": " + exampleObject(spec.content().input())
                    + ", \"response\": " + exampleObject(spec.content().output()) + " }");
        }
        sb.append("  \"x-mock\": {\n").append(String.join(",\n", mocks)).append("\n  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String path(InterfaceSpec spec, boolean more) {
        String reqRef = "#/components/schemas/" + spec.interfaceNo() + "Request";
        String resRef = "#/components/schemas/" + spec.interfaceNo() + "Response";
        return "    " + q(spec.url()) + ": {\n"
                + "      \"post\": {\n"
                + "        \"operationId\": " + q(spec.interfaceNo()) + ",\n"
                + "        \"summary\": " + q(spec.name()) + ",\n"
                + "        \"requestBody\": { \"required\": true, \"content\": { \"application/json\": "
                + "{ \"schema\": { \"$ref\": " + q(reqRef) + " } } } },\n"
                + "        \"responses\": { \"200\": { \"description\": \"OK\", \"content\": "
                + "{ \"application/json\": { \"schema\": { \"$ref\": " + q(resRef) + " } } } } }\n"
                + "      }\n"
                + "    }" + (more ? "," : "") + "\n";
    }

    private String schema(String name, List<InterfaceField> fields) {
        List<String> properties = new ArrayList<>();
        List<String> required = new ArrayList<>();
        for (InterfaceField field : fields) {
            properties.add(q(field.name()) + ": " + property(field));
            if (field.required()) {
                required.add(q(field.name()));
            }
        }
        return q(name) + ": { \"type\": \"object\", \"properties\": { "
                + String.join(", ", properties)
                + " }, \"required\": [ " + String.join(", ", required) + " ] }";
    }

    private String property(InterfaceField field) {
        String base;
        if (!field.children().isEmpty()) {
            List<String> props = new ArrayList<>();
            for (InterfaceField child : field.children()) {
                props.add(q(child.name()) + ": " + property(child));
            }
            base = "{ \"type\": \"object\", \"properties\": { " + String.join(", ", props) + " } }";
        } else {
            base = "{ \"type\": " + q(jsonType(field)) + ", \"example\": " + q(example(field)) + " }";
        }
        return field.list() ? "{ \"type\": \"array\", \"items\": " + base + " }" : base;
    }

    private String exampleObject(List<InterfaceField> fields) {
        List<String> entries = new ArrayList<>();
        for (InterfaceField field : fields) {
            entries.add(q(field.name()) + ": " + example(field));
        }
        return "{ " + String.join(", ", entries) + " }";
    }

    private String example(InterfaceField field) {
        if (!field.children().isEmpty()) {
            return exampleObject(field.children());
        }
        String type = jsonType(field);
        return switch (type) {
            case "integer" -> "0";
            case "number" -> "0.00";
            case "boolean" -> "true";
            default -> q(field.name());
        };
    }

    private String jsonType(InterfaceField field) {
        if (field.dataType() == null) {
            return "string";
        }
        return switch (field.dataType().trim().toUpperCase()) {
            case "INT", "INTEGER", "SMALLINT", "BIGINT", "LONG" -> "integer";
            case "DECIMAL", "NUMERIC", "NUMBER" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            default -> "string";
        };
    }

    private String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
