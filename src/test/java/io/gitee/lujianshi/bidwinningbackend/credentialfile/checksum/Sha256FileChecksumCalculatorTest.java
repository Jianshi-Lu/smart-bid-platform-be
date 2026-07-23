package io.gitee.lujianshi.bidwinningbackend.credentialfile.checksum;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256FileChecksumCalculatorTest {

    private final Sha256FileChecksumCalculator calculator = new Sha256FileChecksumCalculator();

    @Test
    void shouldCalculateStandardSha256Digest() throws Exception {
        String sha256 = calculator.calculate(new ByteArrayInputStream(
                "abc".getBytes(StandardCharsets.UTF_8)));

        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                sha256);
        assertEquals("SHA-256", calculator.algorithm());
    }

    @Test
    void shouldCalculateEmptyContentDigest() throws Exception {
        String sha256 = calculator.calculate(new ByteArrayInputStream(new byte[0]));

        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sha256);
    }
}
