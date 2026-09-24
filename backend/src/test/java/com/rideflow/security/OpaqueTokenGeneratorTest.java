package com.rideflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpaqueTokenGeneratorTest {

    private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

    @Test
    void generatesUrlSafeUniqueTokensWith256BitsOfEntropy() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tokens.add(generator.generate());
        }

        assertThat(tokens).hasSize(1_000);
        // 32 bytes base64url without padding = 43 characters
        assertThat(tokens).allSatisfy(token -> assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void hashIsDeterministicHexSha256() {
        String hash = generator.hash("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(generator.hash("abc")).isEqualTo(hash);
    }
}
