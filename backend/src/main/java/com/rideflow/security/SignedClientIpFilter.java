package com.rideflow.security;

import com.rideflow.security.SignedClientIp.Verification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes a correctly signed {@link SignedClientIp#IP_HEADER} the request's remote address, so the per-IP rate
 * limits key on the browser behind the frontend. Anything else (no headers, a bad or stale signature) leaves
 * the address as the container resolved it (docs/architecture.md section 9): a failed check never widens trust.
 */
public class SignedClientIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SignedClientIpFilter.class);

    private final SignedClientIp signedClientIp;
    private final Map<Verification, Counter> counters = new EnumMap<>(Verification.class);

    public SignedClientIpFilter(SignedClientIp signedClientIp, MeterRegistry meters) {
        this.signedClientIp = signedClientIp;
        for (Verification result : Verification.values()) {
            counters.put(result, Counter.builder("rideflow.client.ip.signatures")
                    .description("Signed client addresses from the frontend, by verification result")
                    .tag("result", result.name().toLowerCase(Locale.ROOT))
                    .register(meters));
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getHeader(SignedClientIp.IP_HEADER);
        String signature = request.getHeader(SignedClientIp.SIGNATURE_HEADER);
        if (ip == null && signature == null) {
            chain.doFilter(request, response);
            return;
        }
        Verification result = ip == null || signature == null
                ? Verification.MALFORMED
                : signedClientIp.verify(ip, signature);
        counters.get(result).increment();
        if (result != Verification.VALID) {
            // Not WARN: anyone can send these headers. A misconfigured secret shows in the metric instead.
            log.debug("Ignoring the signed client address from {}: {}", request.getRemoteAddr(), result);
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new ClientAddressRequest(request, ip), response);
    }

    private static final class ClientAddressRequest extends HttpServletRequestWrapper {

        private final String clientIp;

        private ClientAddressRequest(HttpServletRequest request, String clientIp) {
            super(request);
            this.clientIp = clientIp;
        }

        @Override
        public String getRemoteAddr() {
            return clientIp;
        }

        @Override
        public String getRemoteHost() {
            return clientIp;
        }
    }
}
