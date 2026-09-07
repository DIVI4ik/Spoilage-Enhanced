package com.spoilageenhanced.client;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RENDERDUMP — makes each renderer report what it drew, as text (VISUAL_VERIFICATION.md).
 *
 * <p>A launch log proves the code loaded; it says nothing about what appeared on screen.
 * This mode closes that gap: every HUD line, spoilage bar and tooltip line logs the values
 * it resolved — text, position, colour, size — one line per element, <b>only when that
 * line differs from the last one emitted for the same element</b>. Change-only is the whole
 * design: the render loop runs tens of times a second, so per-frame output is unusable,
 * and the <em>absence</em> of lines becomes evidence in its own right — a countdown that
 * should be ticking and emits one line then silence is exactly the defect, visible without
 * a screen.</p>
 *
 * <p>Off by default, behind a static volatile boolean guarded exactly like
 * {@code SpoilageEnhancedLogger.isTraceEnabled()}. When off it costs one field read per
 * element. Toggled by {@code /spoilage debug renderdump true|false}.</p>
 *
 * <p>Thread-safety: the render thread writes and the log-flusher thread reads the flag;
 * the last-line map is only touched from the render thread, but a ConcurrentHashMap keeps
 * a toggle-during-render from corrupting anything.</p>
 */
public final class RenderDump {

    /** Master switch. Volatile: written by the debug command thread, read every frame.
     *  Can also be forced on at launch via -Dspoilage_enhanced.renderdump=true (headless
     *  verification: the in-game command needs a player, and a launch test has none). */
    private static volatile boolean enabled = Boolean.getBoolean("spoilage_enhanced.renderdump");

    /**
     * Last line emitted per element key. Keyed by a stable element id ("hud", "bar:slot3",
     * "tooltip:minecraft:carrot:2"); the value is the exact line last emitted, so a repeat
     * draw of the same values costs one map get and no log write.
     */
    private static final Map<String, String> LAST_LINE = new ConcurrentHashMap<>();

    /** Cap on distinct element keys, so a pathological scene cannot grow the map unbounded. */
    private static final int MAX_KEYS = 512;

    private RenderDump() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            // A fresh enable must not compare against lines from a previous session.
            LAST_LINE.clear();
        }
    }

    /**
     * Emits {@code RENDERDUMP <line>} through the logger iff the line differs from the last
     * one emitted for this element. Callers pass the element key and the fully built
     * {@code key=value} payload — e.g. {@code hud state=FRESH text="● Fresh" x=120 y=64}.
     *
     * <p>Values that can contain spaces (text) must be quoted by the caller; the format is
     * greppable {@code key=value} pairs per VISUAL_VERIFICATION.md.</p>
     *
     * <p>Pass 603 (L3 — cache correctness): the cap on {@link #MAX_KEYS} was declared but
     * never enforced. A scene with many distinct keys (a creative inventory with 1000+
     * items × 5 tooltip lines each, or many HUD elements) would grow the map without bound
     * and defeat the cap's purpose. When the map exceeds the cap, the oldest entry is
     * removed (ConcurrentHashMap does not guarantee insertion order, so "oldest" is
     * approximate — any key is fine to evict since the next emit() for it will re-add it
     * and the worst case is one extra log line).</p>
     */
    public static void emit(String elementKey, String payload) {
        if (!enabled) {
            return;
        }
        String previous = LAST_LINE.get(elementKey);
        if (payload.equals(previous)) {
            return;
        }
        if (LAST_LINE.size() >= MAX_KEYS && !LAST_LINE.containsKey(elementKey)) {
            // Map is full and this is a new key — evict one entry to make room. Pick the
            // first key returned by the iterator; ConcurrentHashMap iterators are weakly
            // consistent but the choice is arbitrary for a debug tool.
            var first = LAST_LINE.keySet().iterator();
            if (first.hasNext()) {
                LAST_LINE.remove(first.next());
            }
        }
        LAST_LINE.put(elementKey, payload);
        SpoilageEnhancedLogger.log("RENDERDUMP " + payload);
    }

    /** Quotes a value that may contain spaces, for the {@code key=value} line format. */
    public static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "'")) + "\"";
    }

    /** Test hook: the last emitted payload for an element, or null. */
    public static String lastEmitted(String elementKey) {
        return LAST_LINE.get(elementKey);
    }

    /** Test hook: clears both the flag and the last-line map. */
    public static void resetForTest() {
        enabled = false;
        LAST_LINE.clear();
    }
}
