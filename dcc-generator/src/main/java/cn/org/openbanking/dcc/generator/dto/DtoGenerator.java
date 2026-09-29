package cn.org.openbanking.dcc.generator.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.source.JavaTypes;
import cn.org.openbanking.dcc.generator.template.GeneratorProperties;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.type.TypeMappingStrategy;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Generates Java request/response records for a business interface, from the
 * {@code dto.java} template. Fields map to Java types; nested structures become
 * nested generated records; list fields map to {@code List<...>}. The record body
 * is rendered by the template (which iterates the resolved fields), though nested
 * record discovery stays here because it also emits additional files.
 */
@Component
public class DtoGenerator {

    private final TemplateRenderer engine;
    private final GeneratorProperties properties;
    private final TypeMappingStrategy typeMapping;

    public DtoGenerator(TemplateRenderer engine, GeneratorProperties properties, TypeMappingStrategy typeMapping) {
        this.engine = engine;
        this.properties = properties;
        this.typeMapping = typeMapping;
    }

    public List<GeneratedSource> generate(String packageName, String interfaceNo, InterfaceContent content) {
        List<GeneratedSource> sources = new ArrayList<>();
        String base = JavaTypes.capitalize(interfaceNo);
        sources.add(record(packageName, base + "Request", content.input(), sources));
        sources.add(record(packageName, base + "Response", content.output(), sources));
        return sources;
    }

    private GeneratedSource record(String packageName, String name, List<InterfaceField> fields,
            List<GeneratedSource> sink) {
        List<Map<String, Object>> model = new ArrayList<>();
        for (InterfaceField field : fields) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", fieldType(packageName, field, sink));
            entry.put("name", field.name());
            model.add(entry);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("name", name);
        context.put("fields", model);

        String source = engine.process("dto.java", new Context(Locale.ROOT, context));
        return new GeneratedSource(packageName.replace('.', '/') + "/" + name + ".java", source);
    }

    private String fieldType(String packageName, InterfaceField field, List<GeneratedSource> sink) {
        String base;
        if (!field.children().isEmpty()) {
            String childName = JavaTypes.capitalize(field.name()) + properties.getDtoSuffix();
            sink.add(record(packageName, childName, field.children(), sink));
            base = childName;
        } else {
            base = typeMapping.javaType(field.dataType());
        }
        return field.list() ? "List<" + base + ">" : base;
    }
}
