package com.rideflow.service.fare;

import com.rideflow.config.SecurityProperties;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tamper-proof, stateless fare quotes: {@code base64url(json) + "." + base64url(HMAC-SHA256)}. The key is
 * derived from the JWT secret with a distinct label, so a quote can never be replayed as an access token
 * or vice versa. Stateless means quotes survive restarts and work across instances with no shared store.
 */
@Component
public class FareQuoteSigner {

    private static final String HMAC = "HmacSHA256";
    private static final byte[] KEY_DERIVATION_LABEL = "rideflow:fare-quote:v1".getBytes(StandardCharsets.UTF_8);
    private static final String SEPARATOR = ".";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final JsonMapper jsonMapper;

    public FareQuoteSigner(SecurityProperties securityProperties, JsonMapper jsonMapper) {
        byte[] secret = securityProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(mac(new SecretKeySpec(secret, HMAC), KEY_DERIVATION_LABEL), HMAC);
        this.jsonMapper = jsonMapper;
    }

    public String sign(FareQuote quote) {
        String payload = ENCODER.encodeToString(jsonMapper.writeValueAsBytes(quote));
        return payload + SEPARATOR + ENCODER.encodeToString(mac(key, payload.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Verifies the signature and decodes the quote; expiry and ownership are checked by the caller. */
    public FareQuote verify(String token) {
        int separator = token.indexOf(SEPARATOR);
        if (separator <= 0 || separator == token.length() - 1) {
            throw invalid();
        }
        String payload = token.substring(0, separator);
        try {
            byte[] expected = mac(key, payload.getBytes(StandardCharsets.US_ASCII));
            byte[] actual = DECODER.decode(token.substring(separator + 1));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw invalid();
            }
            FareQuote quote = jsonMapper.readValue(DECODER.decode(payload), FareQuote.class);
            if (quote.schemaVersion() != FareQuote.CURRENT_SCHEMA_VERSION) {
                throw invalid();
            }
            return quote;
        } catch (IllegalArgumentException | JacksonException ex) {
            throw invalid();
        }
    }

    private static byte[] mac(SecretKeySpec key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 must be available on every Java platform", ex);
        }
    }

    private static RideFlowException invalid() {
        return new RideFlowException(ErrorCode.QUOTE_INVALID, "Fare quote is invalid; request a new estimate");
    }
}
