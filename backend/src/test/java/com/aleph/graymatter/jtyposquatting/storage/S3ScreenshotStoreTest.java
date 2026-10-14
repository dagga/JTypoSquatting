package com.aleph.graymatter.jtyposquatting.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stockage S3 contre un serveur compatible S3 (S3Mock), ignoré sans moteur de conteneurs.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3ScreenshotStoreTest {

    private static final String BUCKET = "jtypo-screenshots";

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:latest")
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .withEnv("initialBuckets", BUCKET)
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090));

    @Test
    @DisplayName("Écriture, lecture et déduplication sur un stockage compatible S3")
    void testPutGet() {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create("http://" + S3.getHost() + ":" + S3.getMappedPort(9090)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build()) {
            S3ScreenshotStore store = new S3ScreenshotStore(s3, BUCKET);
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 4, 5, 6};

            String key = store.put(png);

            assertEquals(key, store.put(png.clone()));
            assertArrayEquals(png, store.get(key).orElseThrow());
            assertTrue(store.get(ScreenshotStore.keyOf("f".repeat(64))).isEmpty());
        }
    }
}
