package cn.org.openbanking.dcc.generator.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import cn.org.openbanking.dcc.generator.TestTemplates;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;

import org.junit.jupiter.api.Test;

class JarBundleGeneratorTest {

    private final JarBundleGenerator generator = new JarBundleGenerator(TestTemplates.renderer());

    @Test
    void buildsJarWithPomAndSources() throws Exception {
        GeneratedBundle bundle = generator.generate("cn.org.openbanking.dcc.generated", "dcc-acme-env1", "1.2.3",
                List.of(new GeneratedSource("com/acme/Foo.java", "package com.acme; class Foo {}")));

        assertThat(bundle.fileName()).isEqualTo("dcc-acme-env1-1.2.3.jar");
        assertThat(bundle.groupId()).isEqualTo("cn.org.openbanking.dcc.generated");
        assertThat(bundle.version()).isEqualTo("1.2.3");

        Set<String> entries = new HashSet<>();
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(bundle.content()))) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertThat(entries).contains(
                "META-INF/maven/cn.org.openbanking.dcc.generated/dcc-acme-env1/pom.xml",
                "src/main/java/com/acme/Foo.java",
                "META-INF/dcc-artifact.json");
    }
}
