package net.runelite.client.plugins.microbot.kspzombiepirates;

import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;

final class KspZombiePirateLicenseClient {
    private static final String PRODUCT = "ksp-zombie-pirates";

    private KspZombiePirateLicenseClient() {
    }

    static LicenseResult validate(String validationUrl, String licenseKey, String deviceId) {
        if (validationUrl == null || validationUrl.trim().isEmpty()) {
            return LicenseResult.invalid("License validation URL is empty.");
        }

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            return LicenseResult.invalid("License key is empty.");
        }

        if (deviceId == null || deviceId.trim().isEmpty()) {
            return LicenseResult.invalid("Device ID is empty.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(validationUrl.trim());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            String body = "{\"licenseKey\":\"" + escapeJson(licenseKey.trim())
                    + "\",\"product\":\"" + PRODUCT
                    + "\",\"deviceId\":\"" + escapeJson(deviceId.trim()) + "\"}";
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (status < 200 || status >= 300) {
                return LicenseResult.invalid("License server returned HTTP " + status + ". " + trimForUi(responseBody));
            }

            String lower = responseBody.toLowerCase(Locale.ROOT).replace(" ", "");
            if (lower.contains("\"valid\":true") || lower.contains("\"active\":true")) {
                return LicenseResult.valid("License validated.");
            }

            return LicenseResult.invalid(extractMessage(responseBody));
        } catch (Exception ex) {
            return LicenseResult.invalid("License validation failed: " + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String extractMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "License server did not return valid=true.";
        }

        String marker = "\"message\"";
        int markerIndex = responseBody.indexOf(marker);
        if (markerIndex < 0) {
            marker = "\"reason\"";
            markerIndex = responseBody.indexOf(marker);
        }

        if (markerIndex >= 0) {
            int colon = responseBody.indexOf(':', markerIndex + marker.length());
            int firstQuote = responseBody.indexOf('"', colon + 1);
            int secondQuote = responseBody.indexOf('"', firstQuote + 1);
            if (colon >= 0 && firstQuote >= 0 && secondQuote > firstQuote) {
                return responseBody.substring(firstQuote + 1, secondQuote);
            }
        }

        return "License server rejected the key. " + trimForUi(responseBody);
    }

    private static String trimForUi(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180) + "...";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Getter
    static final class LicenseResult {
        private final boolean valid;
        private final String message;

        private LicenseResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message == null || message.isBlank() ? (valid ? "License validated." : "License rejected.") : message;
        }

        static LicenseResult valid(String message) {
            return new LicenseResult(true, message);
        }

        static LicenseResult invalid(String message) {
            return new LicenseResult(false, message);
        }
    }
}
