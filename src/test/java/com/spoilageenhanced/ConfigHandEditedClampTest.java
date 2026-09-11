package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pass 1113 (L7 boundary): a hand-edited config can set the default durations to 0 or
 * negative. Every fallback path returned them raw, so {@code makeFresh} computed
 * {@code expire = now + 0} and all food turned instantly stale — silently. The loader
 * now clamps them, in {@code clampHandEditedValues()}, the same way it already clamped
 * the speed multiplier.
 *
 * <p>Reflection for field access, the same pattern as {@code ConfigCustomisationTest}:
 * the fields are private and Gson-mapped, and widening them for a test would change
 * the serialised shape.</p>
 */
class ConfigHandEditedClampTest {

    private static void set(SpoilageConfig cfg, String name, Object value) throws Exception {
        Field f = SpoilageConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(cfg, value);
    }

    private static long getLong(SpoilageConfig cfg, String name) throws Exception {
        Field f = SpoilageConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(cfg);
    }

    private static double getDouble(SpoilageConfig cfg, String name) throws Exception {
        Field f = SpoilageConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getDouble(cfg);
    }

    private static void clamp(SpoilageConfig cfg) throws Exception {
        java.lang.reflect.Method m = SpoilageConfig.class.getDeclaredMethod("clampHandEditedValues");
        m.setAccessible(true);
        m.invoke(cfg);
    }

    private static SpoilageConfig withDurations(long fresh, long stale, double multiplier) throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        set(cfg, "default_fresh_duration_ticks", fresh);
        set(cfg, "default_stale_duration_ticks", stale);
        set(cfg, "spoilage_speed_multiplier", multiplier);
        clamp(cfg);
        return cfg;
    }

    @Test
    void zeroDurationsClampToDefault() throws Exception {
        SpoilageConfig cfg = withDurations(0L, 0L, 1.0);
        assertEquals(24000L, getLong(cfg, "default_fresh_duration_ticks"),
                "fresh=0 must clamp to 24000, not make every item instantly stale");
        assertEquals(24000L, getLong(cfg, "default_stale_duration_ticks"),
                "stale=0 must clamp to 24000");
    }

    @Test
    void negativeDurationsClampToDefault() throws Exception {
        SpoilageConfig cfg = withDurations(-5000L, -1L, 1.0);
        assertEquals(24000L, getLong(cfg, "default_fresh_duration_ticks"),
                "negative fresh must clamp to 24000");
        assertEquals(24000L, getLong(cfg, "default_stale_duration_ticks"),
                "negative stale must clamp to 24000");
    }

    @Test
    void validDurationsUntouched() throws Exception {
        SpoilageConfig cfg = withDurations(48000L, 96000L, 2.0);
        assertEquals(48000L, getLong(cfg, "default_fresh_duration_ticks"), "valid fresh must survive");
        assertEquals(96000L, getLong(cfg, "default_stale_duration_ticks"), "valid stale must survive");
        assertEquals(2.0, getDouble(cfg, "spoilage_speed_multiplier"), 1e-9, "valid multiplier must survive");
    }

    @Test
    void speedMultiplierClampStillHolds() throws Exception {
        // The pre-existing clamp moved into the same method — pin it so the
        // extraction did not lose it.
        SpoilageConfig zero = withDurations(24000L, 24000L, 0.0);
        assertEquals(1.0, getDouble(zero, "spoilage_speed_multiplier"), 1e-9,
                "multiplier 0 must clamp to 1.0");
        SpoilageConfig negative = withDurations(24000L, 24000L, -3.0);
        assertEquals(1.0, getDouble(negative, "spoilage_speed_multiplier"), 1e-9,
                "negative multiplier must clamp to 1.0");
        SpoilageConfig nan = withDurations(24000L, 24000L, Double.NaN);
        assertEquals(1.0, getDouble(nan, "spoilage_speed_multiplier"), 1e-9,
                "NaN multiplier must clamp to 1.0");
        SpoilageConfig huge = withDurations(24000L, 24000L, 1000.0);
        assertEquals(100.0, getDouble(huge, "spoilage_speed_multiplier"), 1e-9,
                "multiplier >100 must clamp to 100.0");
    }
}
