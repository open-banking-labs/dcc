package cn.org.openbanking.dcc.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * DCC command-line interface. A thin execution client over the /cli/v1 API; all
 * business logic lives in dcc-core (reached through dcc-application).
 *
 * <pre>
 * dcc hash     --type T --model-id N --version V
 * dcc diff     --type T --model-id N --from V1 --to V2
 * dcc bump     --type T --model-id N --from V1 --to V2
 * dcc validate --type T --model-id N --version V
 * dcc export   --type T --model-id N --version V --format java|sql
 * dcc ci       --env-id E --app-id A
 *
 * Common: --server URL (or DCC_SERVER_URL), --output json (or DCC_TOKEN for auth).
 * </pre>
 */
public final class DccCli {

    public static void main(String[] args) {
        try {
            System.exit(new DccCli().run(args));
        } catch (CliException ex) {
            System.err.println("dcc: " + ex.getMessage());
            System.exit(1);
        }
    }

    int run(String[] args) {
        if (args.length == 0) {
            usage();
            return 2;
        }
        String command = args[0];
        Map<String, String> options = parseOptions(args);
        String server = options.getOrDefault("server",
                System.getenv().getOrDefault("DCC_SERVER_URL", "http://localhost:8080"));
        String token = System.getenv().getOrDefault("DCC_TOKEN", "");
        boolean json = "json".equalsIgnoreCase(options.get("output"));
        CliClient client = new CliClient(server, token);

        return switch (command) {
            case "hash" -> hash(client, options, json);
            case "diff" -> diff(client, options, json);
            case "bump" -> bump(client, options, json);
            case "validate" -> validate(client, options, json);
            case "export" -> export(client, options, json);
            case "ci" -> ci(client, options, json);
            case "-h", "--help", "help" -> {
                usage();
                yield 0;
            }
            default -> {
                System.err.println("dcc: unknown command '" + command + "'");
                usage();
                yield 2;
            }
        };
    }

    private int hash(CliClient client, Map<String, String> options, boolean json) {
        JsonNode response = client.post("/cli/v1/hash", modelBody(options));
        print(json, response, response.path("hash").asString());
        return 0;
    }

    private int diff(CliClient client, Map<String, String> options, boolean json) {
        JsonNode response = client.post("/cli/v1/diff", rangeBody(options));
        int count = response.path("changes").size();
        print(json, response, count + " change(s)");
        return 0;
    }

    private int bump(CliClient client, Map<String, String> options, boolean json) {
        JsonNode response = client.post("/cli/v1/bump", rangeBody(options));
        print(json, response, "suggested bump: " + response.path("level").asString());
        return 0;
    }

    private int validate(CliClient client, Map<String, String> options, boolean json) {
        JsonNode response = client.post("/cli/v1/validate", modelBody(options));
        boolean valid = response.path("valid").asBoolean();
        print(json, response, valid ? "valid" : "invalid");
        return valid ? 0 : 1;
    }

    private int export(CliClient client, Map<String, String> options, boolean json) {
        String format = require(options, "format");
        String path = "sql".equalsIgnoreCase(format) ? "/cli/v1/export/sql" : "/cli/v1/export/java";
        JsonNode response = client.post(path, modelBody(options));
        if (json) {
            System.out.println(response.toPrettyString());
        } else {
            for (JsonNode file : response.path("files")) {
                System.out.println("--- " + file.path("path").asString());
                System.out.println(file.path("content").asString());
            }
            System.out.println(response.path("files").size() + " file(s)");
        }
        return 0;
    }

    private int ci(CliClient client, Map<String, String> options, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("environmentId", Long.parseLong(require(options, "env-id")));
        body.put("applicationId", Long.parseLong(require(options, "app-id")));
        JsonNode response = client.post("/cli/v1/drift", body);
        int drifted = response.path("drifted").size();
        print(json, response, drifted == 0 ? "no drift detected" : drifted + " drifted artifact(s)");
        return drifted == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> modelBody(Map<String, String> options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", require(options, "type"));
        body.put("modelId", Long.parseLong(require(options, "model-id")));
        body.put("version", require(options, "version"));
        return body;
    }

    private static Map<String, Object> rangeBody(Map<String, String> options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", require(options, "type"));
        body.put("modelId", Long.parseLong(require(options, "model-id")));
        body.put("fromVersion", require(options, "from"));
        body.put("toVersion", require(options, "to"));
        return body;
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            int equals = key.indexOf('=');
            if (equals >= 0) {
                options.put(key.substring(0, equals), key.substring(equals + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                options.put(key, args[++i]);
            } else {
                options.put(key, "true");
            }
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new CliException("missing required option --" + key);
        }
        return value;
    }

    private static void print(boolean json, JsonNode response, String human) {
        System.out.println(json ? response.toPrettyString() : human);
    }

    private static void usage() {
        System.err.println("""
                dcc <command> [options]

                Commands:
                  hash     --type T --model-id N --version V
                  diff     --type T --model-id N --from V1 --to V2
                  bump     --type T --model-id N --from V1 --to V2
                  validate --type T --model-id N --version V
                  export   --type T --model-id N --version V --format java|sql
                  ci       --env-id E --app-id A

                Options:
                  --server URL     backend base URL (env DCC_SERVER_URL, default http://localhost:8080)
                  --output json    machine-readable output
                Environment:
                  DCC_TOKEN        bearer token (Authorization: Bearer <token>)

                Type T is one of: DATA_STANDARD TABLE_STRUCTURE INTERFACE INTERFACE_TEMPLATE""");
    }
}
