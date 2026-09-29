package cn.org.openbanking.dcc.mcp.prompt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP prompts rendered from external templates (see {@link PromptCatalog}). The text
 * lives in {@code mcp/prompts/*.md.tpl} and can be changed — or localized — without
 * recompiling, and is never HTML-escaped.
 */
@Component
public class PromptTool {

    private final PromptCatalog prompts;

    public PromptTool(PromptCatalog prompts) {
        this.prompts = prompts;
    }

    @McpPrompt(name = "review-change",
            description = "Draft a review checklist for a data-contract change (template-driven).")
    public String reviewChange(
            @McpArg(name = "modelName", description = "Model/artifact name", required = true) String modelName,
            @McpArg(name = "changes", description = "Change list text", required = false) String changes,
            @McpArg(name = "locale", description = "BCP-47 locale, e.g. en or zh", required = false) String locale) {
        Locale resolved = (locale == null || locale.isBlank()) ? null : Locale.forLanguageTag(locale);
        Map<String, Object> model = new HashMap<>();
        model.put("modelName", modelName);
        model.put("changes", changes == null ? "" : changes);
        return prompts.render("review-change", resolved, model);
    }
}
