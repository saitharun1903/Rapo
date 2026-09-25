package com.rideflow.security;

import static com.rideflow.security.SignedClientIpTest.IPV4;
import static com.rideflow.security.SignedClientIpTest.IPV4_SIGNATURE;
import static com.rideflow.security.SignedClientIpTest.SECRET;
import static com.rideflow.security.SignedClientIpTest.SIGNED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SignedClientIpFilterTest {

    private static final String PEER = "10.0.0.5";
    private static final Duration MAX_AGE = Duration.ofSeconds(60);

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final SignedClientIpFilter filter = new SignedClientIpFilter(
            new SignedClientIp(SECRET.getBytes(StandardCharsets.UTF_8), MAX_AGE,
                    Clock.fixed(Instant.ofEpochSecond(SIGNED_AT), ZoneOffset.UTC)),
            meters);

    /** The request the rest of the chain (Spring Security, the controllers) sees. */
    private ServletRequest filtered(String ip, String signature) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(PEER);
        if (ip != null) {
            request.addHeader(SignedClientIp.IP_HEADER, ip);
        }
        if (signature != null) {
            request.addHeader(SignedClientIp.SIGNATURE_HEADER, signature);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest();
    }

    private double counted(String result) {
        return meters.get("rideflow.client.ip.signatures").tag("result", result).counter().count();
    }

    @Test
    void aValidSignatureMakesTheSignedAddressTheRemoteAddress() throws Exception {
        ServletRequest seen = filtered(IPV4, IPV4_SIGNATURE);

        assertThat(seen.getRemoteAddr()).isEqualTo(IPV4);
        assertThat(seen.getRemoteHost()).isEqualTo(IPV4);
        assertThat(counted("valid")).isEqualTo(1);
    }

    @Test
    void withoutTheHeadersTheRequestIsUntouchedAndNotCounted() throws Exception {
        assertThat(filtered(null, null).getRemoteAddr()).isEqualTo(PEER);
        assertThat(meters.get("rideflow.client.ip.signatures").counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    void aForgedOrIncompleteClaimKeepsThePeerAddress() throws Exception {
        assertThat(filtered("203.0.113.9", IPV4_SIGNATURE).getRemoteAddr()).isEqualTo(PEER);
        assertThat(filtered(IPV4, null).getRemoteAddr()).isEqualTo(PEER);
        assertThat(filtered(null, IPV4_SIGNATURE).getRemoteAddr()).isEqualTo(PEER);
        assertThat(filtered("not-an-ip", IPV4_SIGNATURE).getRemoteAddr()).isEqualTo(PEER);

        assertThat(counted("bad_signature")).isEqualTo(1);
        assertThat(counted("malformed")).isEqualTo(3);
        assertThat(counted("valid")).isZero();
    }
}
