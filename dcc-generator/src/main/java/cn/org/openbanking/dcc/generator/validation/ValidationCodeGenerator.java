package cn.org.openbanking.dcc.generator.validation;

import cn.org.openbanking.dcc.core.standard.StandardContent;
import cn.org.openbanking.dcc.generator.source.GeneratedSource;

import org.springframework.stereotype.Component;

/**
 * Generates a Java validation class for a data standard: the standard's constraints
 * (required, length, regex) are emitted as constants plus a {@code validate} method.
 */
@Component
public class ValidationCodeGenerator {

    public GeneratedSource generate(String packageName, String className, String code, StandardContent content) {
        String regexLiteral = content.regex() == null ? "null" : quote(content.regex());
        String source = "package " + packageName + ";\n\n"
                + "import java.util.regex.Pattern;\n\n"
                + "/** Generated validation for data standard \"" + code + "\". */\n"
                + "public final class " + className + " {\n\n"
                + "    public static final String STANDARD = " + quote(code) + ";\n"
                + "    public static final boolean REQUIRED = " + content.required() + ";\n"
                + "    public static final Integer LENGTH = " + content.length() + ";\n"
                + "    public static final String REGEX = " + regexLiteral + ";\n"
                + "    private static final Pattern PATTERN = Pattern.compile(REGEX == null ? \".*\" : REGEX);\n\n"
                + "    private " + className + "() {\n    }\n\n"
                + "    /** @return null when valid, otherwise an error message. */\n"
                + "    public static String validate(String value) {\n"
                + "        if (value == null || value.isEmpty()) {\n"
                + "            return REQUIRED ? " + quote(code + " is required") + " : null;\n"
                + "        }\n"
                + "        if (REGEX != null && !PATTERN.matcher(value).matches()) {\n"
                + "            return " + quote(code + " does not match ") + " + REGEX;\n"
                + "        }\n"
                + "        if (LENGTH != null && value.length() > LENGTH) {\n"
                + "            return " + quote(code + " exceeds length ") + " + LENGTH;\n"
                + "        }\n"
                + "        return null;\n"
                + "    }\n"
                + "}\n";
        return new GeneratedSource(packageName.replace('.', '/') + "/" + className + ".java", source);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
