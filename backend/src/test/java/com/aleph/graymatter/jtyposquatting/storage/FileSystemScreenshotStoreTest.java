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
    @DisplayName("Stockage adressé par contenu : même contenu, même clé, stocké une fois")
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
        store.delete(key); // sans effet si absente
    }

    @Test
    @DisplayName("Une clé ne peut pas sortir du répertoire de stockage")
    void testPathTraversal() {
        FileSystemScreenshotStore store = new FileSystemScreenshotStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.get("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> ScreenshotStore.keyOf("../x"));
    }
}
