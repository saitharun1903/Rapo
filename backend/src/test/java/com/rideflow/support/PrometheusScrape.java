package com.rideflow.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * One read of the management port's {@code /actuator/prometheus}, the text Prometheus itself scrapes, so a test
 * sees the exported series names and labels the dashboards query, not just the in-process meters.
 */
public final class PrometheusScrape {

    private static final int HTTP_OK = 200;

    private final List<String> samples;

    private PrometheusScrape(List<String> samples) {
        this.samples = samples;
    }

    public static PrometheusScrape of(int managementPort) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/prometheus"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HTTP_OK) {
                throw new IllegalStateException("Prometheus scrape answered " + response.statusCode());
            }
            return new PrometheusScrape(response.body().lines().filter(line -> !line.startsWith("#")).toList());
        }
    }

    /** Sum of every sample of {@code name} carrying {@code label="value"}; 0 when there is none. */
    public double value(String name, String label, String value) {
        String selector = label + "=\"" + value + "\"";
        return samples.stream()
                .filter(line -> line.startsWith(name + "{") && line.contains(selector))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .sum();
    }
}
