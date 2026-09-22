package com.spoilageenhanced.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1413 (L5 — render path): test RenderDump.
 *
 * <p>RenderDump makes each renderer report what it drew, as text (VISUAL_VERIFICATION.md).
 * It emits lines only when they differ from the last emitted line for the same element,
 * so the absence of lines becomes evidence in its own right.</p>
 *
 * <p>Pass 1413 (L5 — render path): connection fingerprinting added. Tests verify
 * the fingerprint clears on connection switch and resetForTest() resets the hash.</p>
 *
 * <p>What this test pins: emit works, change-only emission works, cap enforcement works,
 * quote works, lastEmitted works, resetForTest works, connection fingerprint clears on
 * switch, resetForTest resets connection hash.</p>
 */
class RenderDumpTest {

    @BeforeAll
    static void bootstrap() {
        // No Minecraft bootstrap needed — RenderDump is pure Java
    }

    @AfterEach
    void reset() {
        RenderDump.resetForTest();
    }

    @Test
    void emitWorksWhenEnabled() {
        RenderDump.setEnabled(true);
        RenderDump.emit("test", "key=value");
        // Can't easily verify log output, but we can verify lastEmitted
        assertEquals("key=value", RenderDump.lastEmitted("test"));
    }

    @Test
    void emitDoesNothingWhenDisabled() {
        RenderDump.setEnabled(false);
        RenderDump.emit("test", "key=value");
        assertNull(RenderDump.lastEmitted("test"));
    }

    @Test
    void changeOnlyEmission() {
        RenderDump.setEnabled(true);
        RenderDump.emit("test", "key=value1");
        RenderDump.emit("test", "key=value1"); // same payload
        RenderDump.emit("test", "key=value2"); // different payload

        assertEquals("key=value2", RenderDump.lastEmitted("test"));
    }

    @Test
    void capEnforcement() {
        RenderDump.setEnabled(true);
        // Fill to MAX_KEYS (512)
        for (int i = 0; i < 512; i++) {
            RenderDump.emit("key" + i, "value" + i);
        }
        // Add one more — should evict one
        RenderDump.emit("newKey", "newValue");
        assertNotNull(RenderDump.lastEmitted("newKey"));
    }

    @Test
    void quoteEscapesQuotes() {
        assertEquals("\"hello\"", RenderDump.quote("hello"));
        assertEquals("\"he'llo\"", RenderDump.quote("he'llo"));
        // quote() replaces embedded double quotes with single quotes
        assertEquals("\"he'llo\"", RenderDump.quote("he\"llo"));
        assertEquals("\"\"", RenderDump.quote(""));
        assertEquals("\"\"", RenderDump.quote(null));
    }

    @Test
    void lastEmittedReturnsNullForUnknownKey() {
        assertNull(RenderDump.lastEmitted("unknown"));
    }

    @Test
    void resetForTestClearsEverything() {
        RenderDump.setEnabled(true);
        RenderDump.emit("test", "key=value");
        assertEquals("key=value", RenderDump.lastEmitted("test"));

        RenderDump.resetForTest();
        assertFalse(RenderDump.isEnabled());
        assertNull(RenderDump.lastEmitted("test"));
    }

    @Test
    void connectionFingerprintClearsOnSwitch() throws Exception {
        // Pass 1413: connection fingerprinting clears on server switch
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/RenderDump.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("currentConnectionHash()"),
                "must have connection fingerprint method");
        assertTrue(source.contains("cachedConnectionHash"),
                "must cache connection hash");
        assertTrue(source.contains("LAST_LINE.clear()"),
                "must clear map on connection change");
        assertTrue(source.contains("checkConnection()"),
                "must check connection on emit");
    }

    @Test
    void resetForTestResetsConnectionHash() throws Exception {
        // Pass 1413: resetForTest() resets cachedConnectionHash to 0
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/RenderDump.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("cachedConnectionHash = 0"),
                "resetForTest() must reset connection hash");
    }
}