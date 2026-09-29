package cn.org.openbanking.dcc.generator.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Packages the generated sources into a JAR together with a {@code pom.xml} carrying
 * the artifact's Maven coordinates and version. Granularity is one JAR per tenant +
 * environment (the caller supplies the coordinates). The {@code pom.xml} and the
 * {@code dcc-artifact.json} descriptor come from templates.
 */
@Component
public class JarBundleGenerator {

    private final TemplateRenderer engine;

    public JarBundleGenerator(TemplateRenderer engine) {
        this.engine = engine;
    }

    public GeneratedBundle generate(String groupId, String artifactId, String version, List<GeneratedSource> sources) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                JarOutputStream jar = new JarOutputStream(buffer)) {
            addEntry(jar, "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml", pom(groupId, artifactId, version));
            addEntry(jar, "META-INF/dcc-artifact.json",
                    artifactDescriptor(groupId, artifactId, version, sources.size()));
            for (GeneratedSource source : sources) {
                addEntry(jar, "src/main/java/" + source.fileName(), source.content());
            }
            jar.finish();
            return new GeneratedBundle(artifactId + "-" + version + ".jar", groupId, artifactId, version,
                    buffer.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("cannot build artifact bundle", ex);
        }
    }

    private void addEntry(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    private String pom(String groupId, String artifactId, String version) {
        Map<String, Object> model = new HashMap<>();
        model.put("groupId", groupId);
        model.put("artifactId", artifactId);
        model.put("version", version);
        return engine.process("pom.xml", new Context(Locale.ROOT, model));
    }

    private String artifactDescriptor(String groupId, String artifactId, String version, int sourceCount) {
        Map<String, Object> model = new HashMap<>();
        model.put("groupId", groupId);
        model.put("artifactId", artifactId);
        model.put("version", version);
        model.put("sourceCount", sourceCount);
        return engine.process("artifact-descriptor.json", new Context(Locale.ROOT, model));
    }
}
