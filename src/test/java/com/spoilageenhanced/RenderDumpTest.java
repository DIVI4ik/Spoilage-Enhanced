package com.spoilageenhanced;

import com.spoilageenhanced.client.RenderDump;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 442 regression test: RenderDump contract (VISUAL_VERIFICATION.md).
 *
 * <p>RenderDump is a small static utility: a volatile flag plus a last-line map keyed by
 * element id. It exists so a headless verification can read what each renderer resolved
 * without looking at a screen. This test pins the flag, the dedup, the quote helper and
 * the test hook — the parts that run in a unit suite. The emit path itself is exercised
 * by a real client launch (Pass 436), which could not drive the renderers because the
 * headless player is not looking at a block.</p>
 */
public class RenderDumpTest {

    @AfterEach
    void reset() {
        RenderDump.resetForTest();
    }

    @Test
    void defaultIsOff() {
        RenderDump.resetForTest();
        assertFalse(RenderDump.isEnabled(), "RENDERDUMP must be off by default");
    }

    @Test
    void toggleOnAndOff() {
        RenderDump.resetForTest();
        RenderDump.setEnabled(true);
        assertTrue(RenderDump.isEnabled());
        RenderDump.setEnabled(false);
        assertFalse(RenderDump.isEnabled());
    }

    @Test
    void enablingClearsLastLines() {
        RenderDump.resetForTest();
        RenderDump.setEnabled(true);
        RenderDump.emit("hud:FRESH", "element=hud state=FRESH text=\"● Fresh\" x=120 y=64");
        assertEquals("element=hud state=FRESH text=\"● Fresh\" x=120 y=64",
                RenderDump.lastEmitted("hud:FRESH"));
        // A re-enable must not compare against lines from the previous session.
        RenderDump.setEnabled(false);
        RenderDump.setEnabled(true);
        assertNull(RenderDump.lastEmitted("hud:FRESH"),
                "re-enabling must clear the last-line map");
    }

    @Test
    void emitDedupsSamePayload() {
        RenderDump.resetForTest();
        RenderDump.setEnabled(true);
        RenderDump.emit("bar:minecraft:carrot", "element=bar item=\"minecraft:carrot\" fresh=1 stale=0 rotten=0");
        // Second emit of the identical payload must not change the stored line — the
        // dedup is the whole point (a render loop runs tens of times a second).
        RenderDump.emit("bar:minecraft:carrot", "element=bar item=\"minecraft:carrot\" fresh=1 stale=0 rotten=0");
        assertEquals("element=bar item=\"minecraft:carrot\" fresh=1 stale=0 rotten=0",
                RenderDump.lastEmitted("bar:minecraft:carrot"));
    }

    @Test
    void emitChangesWhenPayloadDiffers() {
        RenderDump.resetForTest();
        RenderDump.setEnabled(true);
        RenderDump.emit("hud:STALE", "element=hud state=STALE text=\"● Stale\" x=120 y=64");
        RenderDump.emit("hud:STALE", "element=hud state=STALE text=\"● Stale\" x=121 y=64");
        assertEquals("element=hud state=STALE text=\"● Stale\" x=121 y=64",
                RenderDump.lastEmitted("hud:STALE"));
    }

    @Test
    void emitDoesNothingWhenDisabled() {
        RenderDump.resetForTest();
        // Disabled: emit must be a no-op, so lastEmitted stays null even after a call.
        RenderDump.emit("hud:FRESH", "element=hud state=FRESH text=\"● Fresh\" x=120 y=64");
        assertNull(RenderDump.lastEmitted("hud:FRESH"),
                "emit must not write anything when RENDERDUMP is off");
    }

    @Test
    void quoteEscapesDoubleQuotes() {
        RenderDump.resetForTest();
        // quote() is called by the renderers when building a payload. A value containing
        // a double quote must be escaped so the line stays greppable.
        // The Java literal "a \"quoted\" item" produces the string a "quoted" item.
        String quoted = RenderDump.quote("a \"quoted\" item");
        assertEquals("\"a 'quoted' item\"", quoted,
                "quote() must replace double quotes with single quotes");
        // And a payload built with quote() must not contain a raw double quote.
        String payload = "element=tooltip item=" + RenderDump.quote("minecraft:apple")
                + " line=0 text=" + quoted;
        RenderDump.setEnabled(true);
        RenderDump.emit("tooltip:minecraft:apple", payload);
        String last = RenderDump.lastEmitted("tooltip:minecraft:apple");
        assertNotNull(last);
        assertTrue(last.contains("a 'quoted' item"),
                "quote() must replace double quotes with single quotes, got: " + last);
        assertFalse(last.contains("\"quoted\""),
                "quote() must not leave a raw double quote in the payload, got: " + last);
    }

    @Test
    void quoteHandlesNull() {
        RenderDump.resetForTest();
        // null must become an empty quoted string, not throw.
        assertEquals("\"\"", RenderDump.quote(null));
        assertEquals("\"\"", RenderDump.quote(""));
    }
}