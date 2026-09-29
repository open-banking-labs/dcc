package cn.org.openbanking.dcc.generator.openapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;

import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Generates an OpenAPI 3.0 document (JSON) for a set of business interfaces: one
 * {@code POST} path each, request/response schemas in {@code components}, and an
 * {@code x-mock} block with example payloads so the document supports mocking.
 *
 * <p>The document skeleton comes from the {@code openapi.json} template; the
 * recursively nested paths/schemas/examples fragments are assembled here and
 * injected, because their shape depends on the interface fields.
 */
@Component
public class OpenApiGenerator {

    private final TemplateRenderer engine;
    private final TypeMappingStrategy typeMapping;

    public OpenApiGenerator(TemplateRenderer engine, TypeMappingStrategy typeMapping) {
        this.engine = engine;
        this.typeMapping = typeMapping;
    }

    public String generate(String title, String version, List<InterfaceSpec> specs) {
        StringBuilder paths = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            paths.append(path(specs.get(i), i < specs.size() - 1));
        }

        List<String> schemaDefs = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            schemaDefs.add(schema(spec.interfaceNo() + "Request", spec.content().input()));
            schemaDefs.add(schema(spec.interfaceNo() + "Response", spec.content().output()));
        }

        List<String> mocks = new ArrayList<>();
        for (InterfaceSpec spec : specs) {
            mocks.add("    " + q(spec.interfaceNo()) + ": { \"request\": " + exampleObject(spec.content().input())
                    + ", \"response\": " + exampleObject(spec.content().output()) + " }");
        }

        Map<String, Object> model = new HashMap<>();
        model.put("title", q(title));
        model.put("version", q(version));
        model.put("paths", paths.toString());
        model.put("schemas", String.join(",\n    ", schemaDefs));
        model.put("mocks", String.join(",\n", mocks));
        return engine.process("openapi.json", new Context(Locale.ROOT, model));
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
            base = "{ \"type\": " + q(typeMapping.openApiType(field.dataType())) + ", \"example\": " + q(example(field)) + " }";
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
        String type = typeMapping.openApiType(field.dataType());
        return switch (type) {
            case "integer" -> "0";
            case "number" -> "0.00";
            case "boolean" -> "true";
            default -> q(field.name());
        };
    }

    private String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
