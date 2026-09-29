package cn.org.openbanking.dcc.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceContent;
import cn.org.openbanking.dcc.core.interfaceapi.content.InterfaceField;
import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.core.table.content.TableColumn;
import cn.org.openbanking.dcc.core.table.content.TableContent;
import cn.org.openbanking.dcc.core.table.content.TableIndex;
import cn.org.openbanking.dcc.generator.bundle.GeneratedBundle;
import cn.org.openbanking.dcc.generator.bundle.JarBundleGenerator;
import cn.org.openbanking.dcc.generator.ddl.TableDdl;
import cn.org.openbanking.dcc.generator.ddl.TableDdlGenerator;
import cn.org.openbanking.dcc.generator.dto.DtoGenerator;
import cn.org.openbanking.dcc.generator.openapi.InterfaceSpec;
import cn.org.openbanking.dcc.generator.openapi.OpenApiGenerator;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;
import cn.org.openbanking.dcc.generator.template.GeneratorProperties;
import cn.org.openbanking.dcc.generator.template.GeneratorTemplates;
import cn.org.openbanking.dcc.generator.template.TemplateRenderer;
import cn.org.openbanking.dcc.generator.validation.ValidationCodeGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks the template-driven generators to their pre-templating output (the frozen
 * {@code src/test/resources/golden} fixtures), and proves custom templates can be
 * dropped in and that generated code/SQL is not HTML-escaped.
 */
class TemplatingRegressionTest {

    private final DtoGenerator dto = new DtoGenerator(TestTemplates.renderer(), TestTemplates.properties(), TestTemplates.strategy());
    private final ValidationCodeGenerator validation = new ValidationCodeGenerator(TestTemplates.renderer());
    private final TableDdlGenerator ddl = new TableDdlGenerator(TestTemplates.renderer(), TestTemplates.strategy());
    private final OpenApiGenerator openapi = new OpenApiGenerator(TestTemplates.renderer(), TestTemplates.strategy());
    private final JarBundleGenerator jar = new JarBundleGenerator(TestTemplates.renderer());

    // --- fixtures (identical to the ones used to capture the golden output) ---

    private static InterfaceContent transfer() {
        return new InterfaceContent("转账", "payment", "/transfer", "d", "transfer",
                List.of(new InterfaceField("acctno", 1L, "acctno", "VARCHAR", 32, null, true, false, List.of())),
                List.of(new InterfaceField("status", null, null, "VARCHAR", 8, null, true, false, List.of())));
    }

    private static InterfaceContent nested() {
        return new InterfaceContent("x", null, null, null, null,
                List.of(new InterfaceField("payload", null, null, null, null, null, true, false,
                        List.of(new InterfaceField("amount", null, null, "NUMERIC", 18, 2, true, false, List.of())))),
                List.of());
    }

    private static TableContent accountAfter() {
        TableColumn acctNo =
                new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 32, null, false, true, null, 1, "账号 <A>&\"B\"");
        TableColumn balance =
                new TableColumn("balance", null, null, "NUMERIC", 18, 2, true, false, "0", 2, "余额");
        return new TableContent("Account", "账户表 <t>&\"x\"", "acct_no", List.of(acctNo, balance),
                List.of(new TableIndex("ix_account_acct_no", true, List.of("acct_no"))));
    }

    // --- byte-for-byte regression against the frozen golden output ---

    @Test
    void dtoOutputIsUnchanged() throws IOException {
        assertThat(renderSources(dto.generate("com.acme", "IF0001", transfer()))).isEqualTo(golden("dto_IF0001.txt"));
        assertThat(renderSources(dto.generate("com.acme", "IF2", nested()))).isEqualTo(golden("dto_IF2.txt"));
    }

    @Test
    void validationOutputIsUnchanged() throws IOException {
        StandardContent std = new StandardContent("账号", "d", "VARCHAR", 32, null, true, null, null, "[0-9]+", "123");
        assertThat(validation.generate("com.acme", "AcctnoValidation", "acctno", std).content())
                .isEqualTo(golden("validation.txt"));
    }

    @Test
    void ddlCreateOutputIsUnchanged() throws IOException {
        TableDdl create = ddl.generateCreate("20260902000300", "public", "account", accountAfter(), "sha256:abc123");
        assertThat(create.ddl()).isEqualTo(golden("ddl_create.txt"));
        assertThat(create.flywayFileName()).isEqualTo("V20260902000300__create_account.sql");
        assertThat(create.flywayScript()).startsWith("-- generated from model@sha256:abc123 (account)");
    }

    @Test
    void ddlAlterOutputIsUnchanged() throws IOException {
        TableContent before = new TableContent("Account", "账户表", "acct_no",
                List.of(new TableColumn("acct_no", 1L, "acctno", "VARCHAR", 32, null, false, true, null, 1, "账号")),
                List.of());
        TableDdl alter =
                ddl.generateAlter("20260902000310", "public", "account", before, accountAfter(), "sha256:def456");
        assertThat(alter.flywayScript()).isEqualTo(golden("ddl_alter.txt"));
    }

    @Test
    void openApiOutputIsUnchanged() throws IOException {
        String json = openapi.generate("DCC", "1.0.0",
                List.of(new InterfaceSpec("IF0001", "转账", "/transfer", transfer())));
        assertThat(json).isEqualTo(golden("openapi.txt"));
    }

    @Test
    void jarPomAndDescriptorAreUnchanged() throws Exception {
        GeneratedBundle bundle = jar.generate("cn.org.openbanking.dcc.generated", "dcc-acme-env1", "1.2.3",
                List.of(new GeneratedSource("com/acme/Foo.java", "package com.acme; class Foo {}")));
        assertThat(readJarEntry(bundle, "pom.xml")).isEqualTo(golden("pom.xml"));
        assertThat(readJarEntry(bundle, "dcc-artifact.json")).isEqualTo(golden("artifact-descriptor.json"));
    }

    // --- special characters are NOT HTML-escaped ---

    @Test
    void generatedCodeAndSqlAreNotHtmlEscaped() {
        StandardContent escapeStd = new StandardContent("x", "d", "VARCHAR", 8, null, false, null, null, "[0-9]<>&", null);
        String validationCode = validation.generate("com.acme", "EscapeValidation", "esc", escapeStd).content();
        assertThat(validationCode).contains("REGEX = \"[0-9]<>&\";").doesNotContain("&lt;").doesNotContain("&amp;");

        String createDdl = ddl.generateCreate("20260902000300", "public", "account", accountAfter(), "sha256:x").ddl();
        assertThat(createDdl).contains("IS '账户表 <t>&\"x\"'").doesNotContain("&lt;").doesNotContain("&amp;");
    }

    // --- user templates override the built-in defaults ---

    @Test
    void customTemplateDirectoryOverridesBuiltin(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("dto.java.tpl"), "// custom for [(${name})]\n", StandardCharsets.UTF_8);

        GeneratorProperties properties = new GeneratorProperties();
        properties.setTemplateDir("file:" + dir.toAbsolutePath() + "/");
        TemplateRenderer engine = GeneratorTemplates.create(properties);
        DtoGenerator custom = new DtoGenerator(engine, properties, TestTemplates.strategy());

        List<GeneratedSource> sources = custom.generate("com.acme", "IF9", transfer());
        assertThat(sources.get(0).content()).isEqualTo("// custom for IF9Request\n");
    }

    @Test
    void builtinTemplatesAreUsedWhenCustomDirLacks(@TempDir Path dir) throws IOException {
        // Custom dir exists but has no dto template -> falls back to the classpath default.
        GeneratorProperties properties = new GeneratorProperties();
        properties.setTemplateDir("file:" + dir.toAbsolutePath() + "/");
        DtoGenerator fallback = new DtoGenerator(GeneratorTemplates.create(properties), properties,
                TestTemplates.strategy());

        assertThat(fallback.generate("com.acme", "IF0001", transfer()).get(0).content())
                .isEqualTo(new DtoGenerator(TestTemplates.renderer(), properties, TestTemplates.strategy())
                        .generate("com.acme", "IF0001", transfer()).get(0).content());
    }

    // --- helpers ---

    private String renderSources(List<GeneratedSource> sources) {
        StringBuilder sb = new StringBuilder();
        for (GeneratedSource s : sources) {
            sb.append("=== ").append(s.fileName()).append(" ===\n").append(s.content());
        }
        return sb.toString();
    }

    private String readJarEntry(GeneratedBundle bundle, String suffix) throws IOException {
        try (JarInputStream in = new JarInputStream(new java.io.ByteArrayInputStream(bundle.content()))) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(suffix)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("entry not found: " + suffix);
    }

    private String golden(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("golden/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing golden resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
