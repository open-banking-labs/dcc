package cn.org.openbanking.dcc.generator.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import cn.org.openbanking.dcc.generator.source.GeneratedSource;

import org.springframework.stereotype.Component;

/**
 * Packages the generated sources into a JAR together with a {@code pom.xml} carrying
 * the artifact's Maven coordinates and version. Granularity is one JAR per tenant +
 * environment (the caller supplies the coordinates).
 */
@Component
public class JarBundleGenerator {

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
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>" + groupId + "</groupId>\n"
                + "  <artifactId>" + artifactId + "</artifactId>\n"
                + "  <version>" + version + "</version>\n"
                + "  <packaging>jar</packaging>\n"
                + "  <properties>\n    <maven.compiler.release>17</maven.compiler.release>\n  </properties>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n      <groupId>jakarta.validation</groupId>\n"
                + "      <artifactId>jakarta.validation-api</artifactId>\n      <version>3.0.2</version>\n    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
    }

    private String artifactDescriptor(String groupId, String artifactId, String version, int sourceCount) {
        return "{\n"
                + "  \"groupId\": \"" + groupId + "\",\n"
                + "  \"artifactId\": \"" + artifactId + "\",\n"
                + "  \"version\": \"" + version + "\",\n"
                + "  \"sources\": " + sourceCount + "\n"
                + "}\n";
    }
}
