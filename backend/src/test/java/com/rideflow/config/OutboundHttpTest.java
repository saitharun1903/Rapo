package com.rideflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.entity.EstimateSource;
import com.rideflow.geospatial.GeoPoint;
import com.rideflow.geospatial.OsrmRoutingProvider;
import com.rideflow.geospatial.RouteEstimate;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The outbound clients over a real HTTP connection, against a stand-in that behaves like the public OSRM server:
 * offered deflate, it labels the reply "Content-Encoding: deflate" but sends gzip bytes (captured from
 * router.project-osrm.org on 2026-09-26). A client that offers compression then fails to inflate the body, and every
 * route fell back to a straight line. The clients offer none, and get a plain reply.
 */
class OutboundHttpTest {

    private static final String ROUTE_JSON = """
            {"code":"Ok","routes":[{"distance":7112,"duration":589.6,
             "geometry":{"type":"LineString","coordinates":[[78.4867,17.385],[78.48,17.40],[78.47,17.42]]}}]}
            """;

    private HttpServer server;
    private final AtomicReference<String> acceptEncoding = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route", exchange -> {
            // Every value: a client may send the header more than once (Spring's JDK client does, gzip then deflate).
            String offered = String.join(", ", exchange.getRequestHeaders().getOrDefault("Accept-Encoding", List.of()));
            acceptEncoding.set(offered);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            byte[] body;
            if (offered.isEmpty()) {
                body = ROUTE_JSON.getBytes(StandardCharsets.UTF_8);
            } else {
                // The router's quirk: gzip bytes, labelled "deflate" whenever deflate was offered.
                body = gzip(ROUTE_JSON);
                exchange.getResponseHeaders().add("Content-Encoding", offered.contains("deflate") ? "deflate" : "gzip");
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsRoutesUncompressedSoAMislabelledEncodingCannotBreakThem() {
        RestClient client = OutboundHttp.client(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(4)).build();

        RouteEstimate route = new OsrmRoutingProvider(client).route(new GeoPoint(17.385, 78.4867), new GeoPoint(17.42, 78.47));

        assertThat(acceptEncoding.get()).as("Accept-Encoding sent").isEmpty();
        assertThat(route.source()).isEqualTo(EstimateSource.ROUTED);
        assertThat(route.distanceMeters()).isEqualTo(7112);
        assertThat(route.path()).hasSize(3);
        assertThat(route.path().get(1)).isEqualTo(new GeoPoint(17.40, 78.48));
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
