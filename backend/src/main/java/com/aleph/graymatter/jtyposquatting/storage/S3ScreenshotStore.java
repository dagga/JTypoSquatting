package com.aleph.graymatter.jtyposquatting.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * Screenshots stored in an S3-compatible object storage (web service).
 */
public class S3ScreenshotStore implements ScreenshotStore {

    private final S3Client s3;
    private final String bucket;

    public S3ScreenshotStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String put(byte[] png) {
        String key = ScreenshotStore.keyOf(ScreenshotStore.hash(png));
        if (!exists(key)) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),
                    RequestBody.fromBytes(png));
        }
        return key;
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(builder -> builder.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(builder -> builder.bucket(bucket).key(key));
    }

    private boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
