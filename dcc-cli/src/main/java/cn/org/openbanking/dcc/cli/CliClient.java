package cn.org.openbanking.dcc.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The CLI's transport: POSTs JSON to the {@code /cli/<version>/*} API and parses the
 * JSON back. Uses the JDK {@link HttpClient} for a minimal dependency footprint.
 *
 * <p>The API path version, connect timeout and request timeout are supplied by the
 * caller (see {@link DccCli}), so they are configurable without recompiling.
 */
final class CliClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private final String serverUrl;
    private final String token;
    private final String apiVersion;
    private final Duration requestTimeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    CliClient(String serverUrl, String token, String apiVersion, Duration connectTimeout, Duration requestTimeout) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.token = token;
        this.apiVersion = apiVersion;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /** Builds the versioned API path for a command, e.g. {@code /cli/v1/hash}. */
    String endpoint(String command) {
        return "/cli/" + apiVersion + "/" + command;
    }

    JsonNode post(String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(serverUrl + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (token != null && !token.isBlank()) {
                builder.header(AUTHORIZATION, BEARER + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new CliException("server error " + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new CliException("cannot reach " + serverUrl + path + ": " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CliException("request interrupted");
        } catch (RuntimeException ex) {
            throw new CliException("invalid response from " + serverUrl + path + ": " + ex.getMessage());
        }
    }
}
