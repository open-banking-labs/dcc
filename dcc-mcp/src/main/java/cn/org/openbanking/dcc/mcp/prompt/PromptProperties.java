package cn.org.openbanking.dcc.mcp.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for MCP prompt templates, bound from {@code dcc.mcp.prompts.*}.
 *
 * <pre>
 * dcc:
 *   mcp:
 *     prompts:
 *       dir: classpath:/mcp/prompts/   # override with file:/path/to/custom/
 *       default-locale: en
 * </pre>
 */
@ConfigurationProperties(prefix = "dcc.mcp.prompts")
public class PromptProperties {

    /**
     * Root the prompt templates ({@code *.md.tpl}) are resolved from. Custom files
     * there take precedence over the built-in defaults on the classpath.
     */
    private String dir = "classpath:/mcp/prompts/";

    /** Locale used when a request carries none. */
    private String defaultLocale = "en";

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
