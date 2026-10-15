package com.aleph.graymatter.jtyposquatting.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemScreenshotStoreTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Content-addressed storage: same content, same key, stored once")
    void testPutGet() {
        FileSystemScreenshotStore store = new FileSystemScreenshotStore(dir);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        String key = store.put(png);

        assertTrue(key.matches("screenshots/[0-9a-f]{64}\\.png"));
        assertEquals(key, store.put(png.clone()));
        assertArrayEquals(png, store.get(key).orElseThrow());
        assertTrue(store.get(ScreenshotStore.keyOf("0".repeat(64))).isEmpty());

        store.delete(key);
        assertTrue(store.get(key).isEmpty());
        store.delete(key); // no effect when missing
    }

    @Test
    @DisplayName("A key cannot escape the storage directory")
    void testPathTraversal() {
        FileSystemScreenshotStore store = new FileSystemScreenshotStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.get("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> ScreenshotStore.keyOf("../x"));
    }
}
