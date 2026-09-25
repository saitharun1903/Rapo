package com.rideflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.rideflow.security.SignedClientIp.Verification;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignedClientIpTest {

    /**
     * Shared with frontend/src/lib/clientIp.test.ts, which signs the same inputs: if either side changes the
     * format, its own test fails before the two disagree in production.
     */
    static final String SECRET = "test-only-client-ip-signing-secret-32-bytes-min";
    static final long SIGNED_AT = 1_790_000_000L;
    static final String IPV4 = "198.51.100.7";
    static final String IPV4_SIGNATURE = "v1.1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU";
    static final String IPV6 = "2001:db8::7";
    static final String IPV6_SIGNATURE = "v1.1790000000.n0G0O11IB7I3gK-Ty2Qr1DnJ5XDSXm4K-J5TSHHRNxA";

    private static final Duration MAX_AGE = Duration.ofSeconds(60);

    private static SignedClientIp at(Instant now) {
        return new SignedClientIp(SECRET.getBytes(StandardCharsets.UTF_8), MAX_AGE,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static final SignedClientIp VERIFIER = at(Instant.ofEpochSecond(SIGNED_AT));

    @Test
    void acceptsTheFrontendsSignatureForIpv4AndIpv6() {
        assertThat(VERIFIER.verify(IPV4, IPV4_SIGNATURE)).isEqualTo(Verification.VALID);
        assertThat(VERIFIER.verify(IPV6, IPV6_SIGNATURE)).isEqualTo(Verification.VALID);
    }

    @Test
    void theSignatureCoversTheAddress() {
        assertThat(VERIFIER.verify("198.51.100.8", IPV4_SIGNATURE)).isEqualTo(Verification.BAD_SIGNATURE);
    }

    @Test
    void theSignatureCoversTheTimestamp() {
        String movedInTime = IPV4_SIGNATURE.replace("1790000000", "1790000001");
        assertThat(VERIFIER.verify(IPV4, movedInTime)).isEqualTo(Verification.BAD_SIGNATURE);
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRejected() {
        SignedClientIp otherKey = new SignedClientIp(
                "another-secret-that-is-also-32-bytes-long".getBytes(StandardCharsets.UTF_8), MAX_AGE,
                Clock.fixed(Instant.ofEpochSecond(SIGNED_AT), ZoneOffset.UTC));
        assertThat(otherKey.verify(IPV4, IPV4_SIGNATURE)).isEqualTo(Verification.BAD_SIGNATURE);
    }

    @Test
    void signaturesAreAcceptedOnlyWithinMaxAgeEitherWay() {
        Instant signedAt = Instant.ofEpochSecond(SIGNED_AT);
        assertThat(at(signedAt.plus(MAX_AGE)).verify(IPV4, IPV4_SIGNATURE)).isEqualTo(Verification.VALID);
        assertThat(at(signedAt.minus(MAX_AGE)).verify(IPV4, IPV4_SIGNATURE)).isEqualTo(Verification.VALID);
        assertThat(at(signedAt.plus(MAX_AGE).plusSeconds(1)).verify(IPV4, IPV4_SIGNATURE))
                .isEqualTo(Verification.EXPIRED);
        assertThat(at(signedAt.minus(MAX_AGE).minusSeconds(1)).verify(IPV4, IPV4_SIGNATURE))
                .isEqualTo(Verification.EXPIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "v1.1790000000",
        "v2.1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU",
        "v1.soon.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU",
        "v1.-1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU",
        "v1.1790000000.not base64!",
        "v1.1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU.extra"
    })
    void malformedSignaturesAreRejected(String signature) {
        assertThat(VERIFIER.verify(IPV4, signature)).isEqualTo(Verification.MALFORMED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "0.0.0.0", "255.255.255.255", "::1", "2001:db8::7", "::ffff:198.51.100.7",
        "FE80::1"})
    void ipLiteralsAreRecognised(String value) {
        assertThat(SignedClientIp.isIpLiteral(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "256.1.1.1", "1.2.3", "01.2.3.4", "localhost", "example.com", "abc.def", ".:",
        "1:2:3:4:5:6:7:8:9", "2001:db8::7%eth0", "198.51.100.7, 203.0.113.9", " 198.51.100.7",
        "0000:0000:0000:0000:0000:ffff:198.51.100.7.1"})
    void everythingElseIsNot(String value) {
        assertThat(SignedClientIp.isIpLiteral(value)).isFalse();
    }
}
