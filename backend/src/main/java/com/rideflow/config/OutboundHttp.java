package com.rideflow.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClients for the public services Raido calls (the OSRM router, Nominatim), with connect and read timeouts.
 *
 * <p>Compression is off. Left on, Spring's JDK request factory offers gzip and deflate, and the public OSRM server
 * then labels its reply "deflate" while sending gzip bytes: inflating them fails ("incorrect header check") and every
 * route silently fell back to a straight line. The replies are a few kilobytes, so plain responses cost nothing that
 * matters. Found by running the stack against router.project-osrm.org; OutboundHttpTest reproduces it.
 */
public final class OutboundHttp {

    private OutboundHttp() {
    }

    public static RestClient.Builder client(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
                                            Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        requestFactory.enableCompression(false);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
    }
}
