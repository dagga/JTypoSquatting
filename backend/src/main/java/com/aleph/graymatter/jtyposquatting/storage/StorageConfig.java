package com.aleph.graymatter.jtyposquatting.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Path;

/**
 * Screenshot storage: {@code jtypo.storage.type=filesystem} (default) or {@code s3}.
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "jtypo.storage.type", havingValue = "filesystem", matchIfMissing = true)
    ScreenshotStore fileSystemScreenshotStore(
            @Value("${jtypo.storage.directory:${java.io.tmpdir}/jtyposquatting}") String directory) {
        return new FileSystemScreenshotStore(Path.of(directory));
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "jtypo.storage.type", havingValue = "s3")
    S3Client s3Client(@Value("${jtypo.storage.s3.endpoint:}") String endpoint,
                      @Value("${jtypo.storage.s3.region:us-east-1}") String region,
                      @Value("${jtypo.storage.s3.access-key}") String accessKey,
                      @Value("${jtypo.storage.s3.secret-key}") String secretKey,
                      @Value("${jtypo.storage.s3.path-style:true}") boolean pathStyle) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyle)
                // Checksums only when required: several S3-compatible services reject the newer defaults
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "jtypo.storage.type", havingValue = "s3")
    ScreenshotStore s3ScreenshotStore(S3Client s3, @Value("${jtypo.storage.s3.bucket}") String bucket) {
        return new S3ScreenshotStore(s3, bucket);
    }
}
