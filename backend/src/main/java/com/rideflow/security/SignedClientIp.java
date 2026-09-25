package com.rideflow.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the client address the frontend signs for requests it forwards (its {@code proxy.ts}). The frontend
 * reads the address from a header its hosting edge overwrites (on Vercel, {@code x-vercel-forwarded-for}), so
 * the backend can key per-IP limits on the browser rather than on the frontend server, from whatever address
 * the frontend connects.
 *
 * <p>Wire format, shared with {@code frontend/src/lib/clientIp.ts} (both test suites pin the same vector):
 * {@value #IP_HEADER}: the address; {@value #SIGNATURE_HEADER}: {@code v1.<unix seconds>.<signature>}, where the
 * signature is base64url (unpadded) HMAC-SHA256 over {@code "v1\n" + seconds + "\n" + address}.
 */
public class SignedClientIp {

    public static final String IP_HEADER = "X-RideFlow-Client-IP";
    public static final String SIGNATURE_HEADER = "X-RideFlow-Client-IP-Signature";

    public enum Verification {
        VALID, MALFORMED, BAD_SIGNATURE, EXPIRED
    }

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ".";
    private static final int SIGNATURE_PARTS = 3;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** Longest textual IPv6 address (with an embedded IPv4 part). */
    private static final int MAX_IP_LENGTH = 45;
    /** Unix seconds: up to 19 digits fit in a long. */
    private static final Pattern SECONDS = Pattern.compile("\\d{1,18}");
    private static final Pattern IPV4 = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    private final SecretKeySpec key;
    private final Duration maxAge;
    private final Clock clock;

    public SignedClientIp(byte[] secret, Duration maxAge, Clock clock) {
        this.key = new SecretKeySpec(secret, HMAC_ALGORITHM);
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public Verification verify(String ip, String signatureHeader) {
        if (!isIpLiteral(ip)) {
            return Verification.MALFORMED;
        }
        String[] parts = signatureHeader.split(Pattern.quote(SEPARATOR), -1);
        if (parts.length != SIGNATURE_PARTS || !VERSION.equals(parts[0]) || !SECONDS.matcher(parts[1]).matches()) {
            return Verification.MALFORMED;
        }
        byte[] presented;
        try {
            presented = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException notBase64Url) {
            return Verification.MALFORMED;
        }
        byte[] expected = mac(VERSION + "\n" + parts[1] + "\n" + ip);
        if (!MessageDigest.isEqual(expected, presented)) {
            return Verification.BAD_SIGNATURE;
        }
        Instant signedAt = Instant.ofEpochSecond(Long.parseLong(parts[1]));
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        return age.compareTo(maxAge) > 0 ? Verification.EXPIRED : Verification.VALID;
    }

    private byte[] mac(String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(HMAC_ALGORITHM + " is unavailable", ex);
        }
    }

    /** A textual IPv4 or IPv6 address. Never resolves a name: IPv6 literals are parsed, not looked up. */
    static boolean isIpLiteral(String value) {
        if (value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":") || !IPV6_CHARACTERS.matcher(value).matches()) {
            return false;
        }
        try {
            // Starting with a hex digit or ':' and containing ':', it is parsed as an IPv6 literal (an IPv4-mapped
            // one comes back as Inet4Address) or rejected; it is never looked up in DNS.
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException notAnAddress) {
            return false;
        }
    }
}
