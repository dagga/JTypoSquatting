package com.aleph.graymatter.jtyposquatting.client;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.google.gson.Gson;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;

public class JTypoSquattingRestClient {

    private static final Logger logger = LoggerFactory.getLogger(JTypoSquattingRestClient.class);
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public JTypoSquattingRestClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.gson = new Gson();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 404;
        } catch (Exception e) {
            return false;
        }
    }

    public AutoCloseable streamDomainChecks(String domain, Consumer<String> onResult, Runnable onComplete, Consumer<String> onError) {
        try {
            String encodedDomain = URLEncoder.encode(domain, StandardCharsets.UTF_8);
            URL url = new URL(baseUrl + "/api/generate-and-check?domain=" + encodedDomain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(0);
            connection.setConnectTimeout(10000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                Thread t = new Thread(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if (!data.isEmpty()) {
                                    onResult.accept(data);
                                    try {
                                        sleep(5);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                        if (!Thread.currentThread().isInterrupted()) {
                            onComplete.run();
                        }
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            onError.accept("Stream read error: " + e.getMessage());
                        }
                    } finally {
                        try { reader.close(); } catch (Exception ignored) {}
                        connection.disconnect();
                    }
                }, "SSE-Reader-Thread");
                return getAutoCloseable(connection, reader, t);
            } else {
                onError.accept("Server returned error code: " + connection.getResponseCode());
                return () -> {};
            }
        } catch (Exception e) {
            onError.accept("Failed to connect to stream: " + e.getMessage());
            return () -> {};
        }
    }

    @NonNull
    private AutoCloseable getAutoCloseable(HttpURLConnection connection, BufferedReader reader, Thread t) {
        t.setDaemon(true);
        t.start();

        return () -> {
            try {
                t.interrupt();
                try { reader.close(); } catch (Exception ignored) {}
                connection.disconnect();
            } catch (Exception e) {
                // ignore
            }
        };
    }
    /**
     * Analyze a domain page and save to backend database (streaming)
     */
    public AutoCloseable streamAnalyzeAndSave(String domain, Consumer<String> onResult, Runnable onComplete, Consumer<String> onError) {
        try {
            String encodedDomain = URLEncoder.encode(domain, StandardCharsets.UTF_8);
            URL url = new URL(baseUrl + "/api/data/analyze-and-save?domain=" + encodedDomain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setReadTimeout(0);
            connection.setConnectTimeout(10000);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                Thread t = new Thread(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (Thread.currentThread().isInterrupted()) break;
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if (!data.isEmpty()) {
                                    onResult.accept(data);
                                }
                            }
                        }
                        if (!Thread.currentThread().isInterrupted()) {
                            onComplete.run();
                        }
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            onError.accept("Stream error: " + e.getMessage());
                        }
                    } finally {
                        try { reader.close(); } catch (Exception ignored) {}
                        connection.disconnect();
                    }
                }, "Analyze-SSE-Reader");
                return getAutoCloseable(connection, reader, t);
            } else {
                onError.accept("Server returned: " + connection.getResponseCode());
                return () -> {};
            }
        } catch (Exception e) {
            onError.accept("Failed to analyze: " + e.getMessage());
            return () -> {};
        }
    }

    /**
     * Get cached domain data from backend database
     */
    public DomainPageDTO getCachedData(String domain) {
        try {
            URL url = new URL(baseUrl + "/api/data/cached/" + URLEncoder.encode(domain, StandardCharsets.UTF_8));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                return gson.fromJson(json.toString(), DomainPageDTO.class);
            }
        } catch (Exception e) {
            // Ignore fetching errors
        }
        return null;
    }

    /**
     * Get statistics from backend
     */
    public Map<String, Object> getStats() {
        try {
            URL url = new URL(baseUrl + "/api/data/stats");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();
                @SuppressWarnings("unchecked")
                Map<String, Object> result = gson.fromJson(json.toString(), Map.class);
                return result;
            }
        } catch (Exception e) {
            // Ignore stats fetching errors
        }
        return new HashMap<>();
    }

    /**
     * Clear all cached data on backend
     */
    public boolean clearAllCachedData() {
        try {
            URL url = new URL(baseUrl + "/api/data/all");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setConnectTimeout(5000);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            // Ignore clearing errors
            return false;
        }
    }

    /**
     * Cancel all active analysis and clear database in one call.
     * This is more efficient than calling cancel() and clearAllCachedData() separately.
     */
    public void cancelAndClear() {
        try {
            URL url = new URL(baseUrl + "/api/cancel-and-clear");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setConnectTimeout(5000);
            int responseCode = connection.getResponseCode();
            logger.debug("Cancel and clear response: {}", responseCode);
        } catch (Exception e) {
            logger.error("Error during cancel and clear: {}", e.getMessage());
        }
    }

    /**
     * Cancel active analysis on backend
     */
    public void cancelActiveAnalysis() {
        try {
            URL url = new URL(baseUrl + "/api/cancel");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.getResponseCode(); // Trigger the request
            connection.disconnect();
        } catch (Exception e) {
            // Ignore canceling errors
        }
    }


}
