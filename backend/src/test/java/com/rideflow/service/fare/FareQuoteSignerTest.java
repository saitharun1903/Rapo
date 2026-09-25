package com.rideflow.service.fare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rideflow.config.SecurityProperties;
import com.rideflow.entity.EstimateSource;
import com.rideflow.entity.FareBreakdown;
import com.rideflow.entity.VehicleCategory;
import com.rideflow.exception.ErrorCode;
import com.rideflow.exception.RideFlowException;
import com.rideflow.geospatial.GeoPoint;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class FareQuoteSignerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static FareQuoteSigner signer(String secret) {
        return new FareQuoteSigner(new SecurityProperties(
                new SecurityProperties.Jwt(secret, "rideflow", Duration.ofMinutes(15)),
                new SecurityProperties.RefreshToken(Duration.ofDays(14), Duration.ofDays(7), Duration.ofSeconds(10), "rf", true, "Lax", "/"),
                4), JSON);
    }

    private static FareQuote quote() {
        FareBreakdown.Amounts fare = new FareBreakdown.Amounts(new BigDecimal("40.00"), new BigDecimal("154.08"),
                new BigDecimal("43.01"), new BigDecimal("237.09"), new BigDecimal("1.20"), new BigDecimal("10.00"),
                new BigDecimal("80.00"), false, new BigDecimal("295.00"), "INR", 12_840, 1_720, "v1");
        return new FareQuote(FareQuote.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), VehicleCategory.ECONOMY,
                new GeoPoint(17.4435, 78.3772), new GeoPoint(17.4239, 78.4738), 12_840, 1_720,
                EstimateSource.ROUTED, fare, Instant.parse("2026-09-24T10:05:00Z"));
    }

    private final FareQuoteSigner signer = signer("unit-test-secret-with-at-least-32-bytes!");

    @Test
    void roundTripsExactValuesIncludingMoney() {
        FareQuote original = quote();

        FareQuote decoded = signer.verify(signer.sign(original));

        assertThat(decoded).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(original);
    }

    @Test
    void tamperedPriceIsRejected() {
        String token = signer.sign(quote());
        String payloadJson = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
                StandardCharsets.UTF_8);
        String cheaper = payloadJson.replace("ECONOMY", "XL").replace("295.00", "1.00");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(cheaper.getBytes(StandardCharsets.UTF_8))
                + token.substring(token.indexOf('.'));

        assertInvalid(forged);
    }

    @Test
    void quoteSignedWithAnotherSecretIsRejected() {
        String foreign = signer("a-completely-different-secret-of-32-bytes").sign(quote());

        assertInvalid(foreign);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", "abc", "abc.", ".abc", "not-base64!.sig", "e30.e30"})
    void malformedTokensAreRejected(String token) {
        assertInvalid(token);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> signer.verify(token))
                .isInstanceOf(RideFlowException.class)
                .extracting(ex -> ((RideFlowException) ex).code())
                .isEqualTo(ErrorCode.QUOTE_INVALID);
    }
}
