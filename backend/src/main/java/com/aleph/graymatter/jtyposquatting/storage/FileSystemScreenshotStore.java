package com.aleph.graymatter.jtyposquatting.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Screenshots stored in a local directory (desktop application, development).
 */
public class FileSystemScreenshotStore implements ScreenshotStore {

    private final Path root;

    public FileSystemScreenshotStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String put(byte[] png) {
        String key = ScreenshotStore.keyOf(ScreenshotStore.hash(png));
        Path file = resolve(key);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Path tmp = Files.createTempFile(file.getParent(), "upload", ".tmp");
                Files.write(tmp, png);
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path file = resolve(key);
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Invalid key");
        }
        return file;
    }
}
