package cn.org.openbanking.dcc.core.id;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised identifier rules, bound from {@code dcc.id.*}.
 *
 * <pre>
 * dcc:
 *   id:
 *     rules:
 *       DATA_STANDARD:
 *         prefix: ""
 *         separator: ""
 *         parts:
 *           - { type: TYPE }
 *           - { type: TENANT, width: 3 }
 *           - { type: APPLICATION, width: 3 }
 *           - { type: SEQUENCE, width: 8 }
 *         sequence:
 *           source: db-segment
 *           key: "artifact:{type}:{tenant}:{application}"
 *           step: 1
 *           max-value: 99999999
 * </pre>
 *
 * <p>A configured rule replaces the built-in rule of the same code wholesale; rules
 * that are not mentioned keep their built-in defaults.
 */
@ConfigurationProperties(prefix = "dcc.id")
public class IdProperties {

    private Map<String, Rule> rules = new LinkedHashMap<>();

    public Map<String, Rule> getRules() {
        return rules;
    }

    public void setRules(Map<String, Rule> rules) {
        this.rules = rules == null ? new LinkedHashMap<>() : rules;
    }

    public static class Rule {

        private String prefix = "";
        private String suffix = "";
        private String separator = "";
        private Integer maxLength;
        private List<Part> parts = new ArrayList<>();
        private Sequence sequence;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        public String getSeparator() {
            return separator;
        }

        public void setSeparator(String separator) {
            this.separator = separator;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
        }

        public List<Part> getParts() {
            return parts;
        }

        public void setParts(List<Part> parts) {
            this.parts = parts == null ? new ArrayList<>() : parts;
        }

        public Sequence getSequence() {
            return sequence;
        }

        public void setSequence(Sequence sequence) {
            this.sequence = sequence;
        }
    }

    public static class Part {

        private String type;
        private String value;
        private Integer width;
        private Integer radix;
        private String pad;
        private String pattern;
        private String zone;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getRadix() {
            return radix;
        }

        public void setRadix(Integer radix) {
            this.radix = radix;
        }

        public String getPad() {
            return pad;
        }

        public void setPad(String pad) {
            this.pad = pad;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }

    public static class Sequence {

        private String source = SequenceSpec.DB_SEGMENT;
        private String key;
        private Integer step = 1;
        private Long maxValue;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Integer getStep() {
            return step;
        }

        public void setStep(Integer step) {
            this.step = step;
        }

        public Long getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(Long maxValue) {
            this.maxValue = maxValue;
        }
    }
}
