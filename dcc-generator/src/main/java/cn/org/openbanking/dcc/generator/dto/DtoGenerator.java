package cn.org.openbanking.dcc.generator.dto;

import java.util.ArrayList;
import java.util.List;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.source.JavaTypes;

import org.springframework.stereotype.Component;

/**
 * Generates Java request/response records for a business interface. Fields map to
 * Java types; nested structures become nested generated records; list fields map to
 * {@code List<...>}.
 */
@Component
public class DtoGenerator {

    public List<GeneratedSource> generate(String packageName, String interfaceNo, InterfaceContent content) {
        List<GeneratedSource> sources = new ArrayList<>();
        String base = JavaTypes.capitalize(interfaceNo);
        sources.add(record(packageName, base + "Request", content.input(), sources));
        sources.add(record(packageName, base + "Response", content.output(), sources));
        return sources;
    }

    private GeneratedSource record(String packageName, String name, List<InterfaceField> fields,
            List<GeneratedSource> sink) {
        StringBuilder components = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            InterfaceField field = fields.get(i);
            components.append("        ").append(fieldType(packageName, field, sink)).append(' ').append(field.name());
            if (i < fields.size() - 1) {
                components.append(',');
            }
            components.append('\n');
        }
        String source = "package " + packageName + ";\n\n"
                + "import java.util.List;\n\n"
                + "/** Generated DTO for " + name + ". */\n"
                + "public record " + name + "(\n" + components + ") {\n}\n";
        return new GeneratedSource(packageName.replace('.', '/') + "/" + name + ".java", source);
    }

    private String fieldType(String packageName, InterfaceField field, List<GeneratedSource> sink) {
        String base;
        if (!field.children().isEmpty()) {
            String childName = JavaTypes.capitalize(field.name()) + "Dto";
            sink.add(record(packageName, childName, field.children(), sink));
            base = childName;
        } else {
            base = JavaTypes.toJavaType(field.dataType(), field.length(), field.scale());
        }
        return field.list() ? "List<" + base + ">" : base;
    }
}
