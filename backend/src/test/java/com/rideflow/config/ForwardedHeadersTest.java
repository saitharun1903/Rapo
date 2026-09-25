package com.rideflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/**
 * Who the application's own server settings (application.yml) say sent a request. The per-IP rate limits key on
 * {@code getRemoteAddr()}, and CORS and the WebSocket Origin check compare against the request's scheme, host and
 * port, so forwarding headers may change them only when the connection comes from a proxy in
 * {@code TRUSTED_PROXIES}. A real Tomcat with a bare servlet: forwarding is applied by a Tomcat valve, which
 * MockMvc never runs, and no Docker is needed.
 */
class ForwardedHeadersTest {

    private static final String LOOPBACK = "127.0.0.1";
    private static final String FORGED_IP = "203.0.113.9";
    private static final String CLIENT_IP = "198.51.100.7";
    private static final String INNER_PROXY_IP = "10.0.0.5";
    private static final String FORWARDED_HOST = "app.example";
    private static final String FORWARDED_PORT = "443";

    @Nested
    @SpringBootTest(classes = EchoServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class WhenNoProxyIsTrusted {

        @LocalServerPort
        private int port;

        @Test
        void aForgedXForwardedForDoesNotChangeTheClientAddress() throws Exception {
            Map<String, String> seen = echo(port, "X-Forwarded-For", FORGED_IP);

            assertThat(seen).containsEntry("remoteAddr", LOOPBACK);
        }

        @Test
        void forgedSchemeHostAndPortAreIgnored() throws Exception {
            Map<String, String> seen = echo(port,
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", FORWARDED_HOST,
                    "X-Forwarded-Port", FORWARDED_PORT);

            assertThat(seen)
                    .containsEntry("scheme", "http")
                    .containsEntry("secure", "false")
                    .containsEntry("serverName", LOOPBACK)
                    .containsEntry("serverPort", Integer.toString(port));
        }

        @Test
        void aForgedRfc7239ForwardedHeaderIsIgnored() throws Exception {
            Map<String, String> seen = echo(port,
                    "Forwarded", "for=" + FORGED_IP + ";proto=https;host=" + FORWARDED_HOST);

            assertThat(seen)
                    .containsEntry("remoteAddr", LOOPBACK)
                    .containsEntry("scheme", "http")
                    .containsEntry("serverName", LOOPBACK);
        }
    }

    /** The test client connects from loopback, so trusting loopback makes it play the reverse proxy. */
    @Nested
    @SpringBootTest(classes = EchoServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "TRUSTED_PROXIES=127.0.0.0/8,10.0.0.0/8")
    class BehindATrustedProxy {

        @LocalServerPort
        private int port;

        @Test
        void theClientIsTheRightMostUntrustedHop() throws Exception {
            // The client sent its own X-Forwarded-For; the proxy appended the address it saw.
            Map<String, String> seen = echo(port, "X-Forwarded-For", FORGED_IP + ", " + CLIENT_IP);

            assertThat(seen).containsEntry("remoteAddr", CLIENT_IP);
        }

        @Test
        void trustedHopsInTheChainAreSkipped() throws Exception {
            Map<String, String> seen = echo(port,
                    "X-Forwarded-For", FORGED_IP + ", " + CLIENT_IP + ", " + INNER_PROXY_IP);

            assertThat(seen).containsEntry("remoteAddr", CLIENT_IP);
        }

        @Test
        void withoutXForwardedForTheProxyIsTheClient() throws Exception {
            assertThat(echo(port)).containsEntry("remoteAddr", LOOPBACK);
        }

        @Test
        void schemeHostAndPortComeFromTheProxy() throws Exception {
            Map<String, String> seen = echo(port,
                    "X-Forwarded-Proto", "https",
                    "X-Forwarded-Host", FORWARDED_HOST,
                    "X-Forwarded-Port", FORWARDED_PORT);

            assertThat(seen)
                    .containsEntry("scheme", "https")
                    .containsEntry("secure", "true")
                    .containsEntry("serverName", FORWARDED_HOST)
                    .containsEntry("serverPort", FORWARDED_PORT);
        }
    }

    private static Map<String, String> echo(int port, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://" + LOOPBACK + ":" + port + "/echo"));
        if (headers.length > 0) {
            request.headers(headers);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(HttpServletResponse.SC_OK);
            return Arrays.stream(response.body().split("\n"))
                    .map(line -> line.split("=", 2))
                    .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        }
    }

    /** Only the servlet container, configured from application.yml exactly as the application's is. */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(TomcatServletWebServerAutoConfiguration.class)
    static class EchoServer {

        @Bean
        ServletRegistrationBean<HttpServlet> echoServlet() {
            return new ServletRegistrationBean<>(new EchoServlet(), "/echo");
        }
    }

    private static final class EchoServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write(String.join("\n",
                    "remoteAddr=" + request.getRemoteAddr(),
                    "scheme=" + request.getScheme(),
                    "secure=" + request.isSecure(),
                    "serverName=" + request.getServerName(),
                    "serverPort=" + request.getServerPort()));
        }
    }
}
