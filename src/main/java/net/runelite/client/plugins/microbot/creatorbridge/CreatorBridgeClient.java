package net.runelite.client.plugins.microbot.creatorbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public final class CreatorBridgeClient
{
    private static final String LOCALHOST = "127.0.0.1";
    private static final String HEARTBEAT_PATH = "/api/microbot/heartbeat";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final Gson gson;
    private final HttpClient httpClient;

    @Getter
    private final int port;

    @Getter
    private final long pid;

    private final String launchToken;

    public CreatorBridgeClient(int port, long pid, String launchToken)
    {
        if (port <= 0 || port > 65535)
        {
            throw new IllegalArgumentException("Invalid creator API port: " + port);
        }

        this.port = port;
        this.pid = pid;
        this.launchToken = launchToken == null ? "" : launchToken.trim();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    public CreatorBridgeResponse sendHeartbeat(CreatorBridgePayload payload)
    {
        if (payload == null)
        {
            return CreatorBridgeResponse.failed("Payload was null");
        }

        try
        {
            HttpRequest request = buildPostRequest(getHeartbeatUrl(), gson.toJson(payload));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode < 200 || statusCode >= 300)
            {
                return CreatorBridgeResponse.failed("Creator API returned HTTP " + statusCode + ": " + safeBody(body));
            }

            return CreatorBridgeResponse.success(statusCode, body);
        }
        catch (IOException ex)
        {
            return CreatorBridgeResponse.failed("Creator API connection failed: " + ex.getMessage());
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return CreatorBridgeResponse.failed("Creator API request interrupted");
        }
        catch (Exception ex)
        {
            return CreatorBridgeResponse.failed("Creator API request failed: " + ex.getMessage());
        }
    }

    public String getHeartbeatUrl()
    {
        return "http://" + LOCALHOST + ":" + port + HEARTBEAT_PATH;
    }

    private HttpRequest buildPostRequest(String url, String json)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Creator-Pid", String.valueOf(pid))
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (!launchToken.isBlank())
        {
            builder.header("X-Creator-Token", launchToken);
        }

        return builder.build();
    }

    private String safeBody(String body)
    {
        if (body == null || body.isBlank())
        {
            return "";
        }

        String trimmed = body.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    public static final class CreatorBridgeResponse
    {
        private final boolean success;
        private final int statusCode;
        private final String body;
        private final String errorMessage;

        private CreatorBridgeResponse(boolean success, int statusCode, String body, String errorMessage)
        {
            this.success = success;
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public static CreatorBridgeResponse success(int statusCode, String body)
        {
            return new CreatorBridgeResponse(true, statusCode, body, "");
        }

        public static CreatorBridgeResponse failed(String errorMessage)
        {
            return new CreatorBridgeResponse(false, -1, "", errorMessage);
        }

        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public String getErrorMessage() { return errorMessage; }
    }
}
