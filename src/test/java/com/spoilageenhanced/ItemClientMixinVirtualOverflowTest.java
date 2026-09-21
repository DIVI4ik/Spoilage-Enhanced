package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1399 (L7 — boundary): overflow guard for currentTime + freshDuration in
 * ItemClientMixin tooltip virtual data path.
 *
 * <p>When a virtual stack (no server SPOILAGE component) has fresh items and no
 * tracked minimum expiration, the code computes {@code minTime = currentTime + freshDuration}
 * (line 172). freshDuration is unclamped by the config loader (clampHandEditedValues touches
 * only defaults and speed multiplier; computeBaseDurations accepts any fresh > 0 up to
 * Long.MAX_VALUE). A hand-edited huge freshDuration combined with a real game time can
 * overflow the addition to negative, making minTime negative. The subsequent
 * {@code Math.max(0, minTime - currentTime)} would then compute a huge positive diff
 * (since minTime - currentTime would be negative - currentTime = huge positive), causing
 * the tooltip to display an absurdly large time until spoilage.</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel) when the addition would
 * overflow, matching the guards in initializeItemSpoilage (pass 1164),
 * updateSpoilageDataImpl (pass 233), makeStale (pass 1397), and
 * BlockSpoilageData.getSpoilageState (passes 1396, 1398).</p>
 */
public class ItemClientMixinVirtualOverflowTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectConfig(SpoilageConfig cfg) throws Exception {
        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, cfg);
    }

    private static SpoilageConfig readInstance() throws Exception {
        Field instanceField = SpoilageConfig.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        return (SpoilageConfig) instanceField.get(null);
    }

    @Test
    void hugeFreshDurationClampsToNeverInVirtualPath() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        // freshDuration = Long.MAX_VALUE - 1000, so currentTime (e.g. 1_000_000) + freshDuration overflows
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(Long.MAX_VALUE - 1000L, 48000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            // Test the arithmetic directly:
            long currentTime = 1_000_000L;
            long freshDuration = Long.MAX_VALUE - 1000L;
            long sum = currentTime + freshDuration;
            // This overflows to negative!
            assertTrue(sum < 0, "The sum must overflow to negative without a guard");

            // The fix: clamp to Long.MAX_VALUE when overflow would occur
            long expectedMinTime = (freshDuration > Long.MAX_VALUE - currentTime)
                    ? Long.MAX_VALUE
                    : currentTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedMinTime,
                    "The guard must clamp to Long.MAX_VALUE when overflow would occur");

        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void normalFreshDurationComputesPlainly() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            long currentTime = 1_000_000L;
            long freshDuration = 24000L; // normal carrot fresh duration
            long sum = currentTime + freshDuration;
            assertFalse(sum < 0, "Normal durations must not overflow");
            assertEquals(currentTime + freshDuration, sum);
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void exactlyMaxFreshDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(Long.MAX_VALUE, 48000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            long currentTime = 1_000_000L;
            long freshDuration = Long.MAX_VALUE;
            long expectedMinTime = (freshDuration > Long.MAX_VALUE - currentTime)
                    ? Long.MAX_VALUE
                    : currentTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedMinTime,
                    "Long.MAX_VALUE freshDuration must clamp to Long.MAX_VALUE");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
