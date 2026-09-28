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
 * The CLI's transport: POSTs JSON to the {@code /cli/v1/*} API and parses the JSON
 * back. Uses the JDK {@link HttpClient} for a minimal dependency footprint.
 */
final class CliClient {

    private final String serverUrl;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    CliClient(String serverUrl, String token) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.token = token;
    }

    JsonNode post(String path, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(serverUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
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
