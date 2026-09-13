package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1168 regression test: clampHandEditedValues and applySpeedMultiplier boundary checks.
 */
public class ConfigBoundaryClampTest {

    @Test
    void hugeBaseDurationWithMinimumSpeedMultiplierDoesNotOverflow() throws Exception {
        SpoilageConfig config = new SpoilageConfig();
        config.setSpoilageSpeedMultiplier(0.01); // 100x slower

        Method applyMethod = SpoilageConfig.class.getDeclaredMethod("applySpeedMultiplier", long.class);
        applyMethod.setAccessible(true);

        long hugeDuration = Long.MAX_VALUE / 2;
        long result = (long) applyMethod.invoke(config, hugeDuration);

        assertEquals(Long.MAX_VALUE, result,
                "Scaling a huge base duration by 0.01 multiplier (x100) must clamp to Long.MAX_VALUE, not wrap negative");
    }

    @Test
    void negativeItemDurationsInConfigAreClamped() throws Exception {
        SpoilageConfig config = new SpoilageConfig();
        config.item_durations_for_test().put("minecraft:apple", new SpoilageConfig.ItemDuration(-100L, -50L));

        Method clampMethod = SpoilageConfig.class.getDeclaredMethod("clampHandEditedValues");
        clampMethod.setAccessible(true);
        clampMethod.invoke(config);

        SpoilageConfig.ItemDuration dur = config.item_durations_for_test().get("minecraft:apple");
        assertEquals(24000L, dur.fresh, "Negative fresh duration must be clamped to default 24000");
        assertEquals(24000L, dur.stale, "Negative stale duration must be clamped to default 24000");
    }
}
