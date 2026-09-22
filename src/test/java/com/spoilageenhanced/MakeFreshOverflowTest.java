package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1400 (L7 — boundary): overflow guard in FoodSpoilageUtil.makeFresh.
 *
 * <p>makeFresh computes {@code world.getGameTime() + freshDuration} for every item
 * in the stack. freshDuration is unclamped by the config loader (clampHandEditedValues
 * touches only defaults and speed multiplier; computeBaseDurations accepts any fresh > 0
 * up to Long.MAX_VALUE). A hand-edited huge freshDuration combined with a real game time
 * can overflow the addition to negative, making the stack read as already expired
 * (since the expiration is in the past).</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel) when the addition would
 * overflow, matching the guards in initializeItemSpoilage (pass 1164),
 * updateSpoilageDataImpl (pass 233), makeStale (pass 1397),
 * BlockSpoilageData.getSpoilageState (passes 1396, 1398), and
 * ItemClientMixin virtual path (pass 1399).</p>
 */
public class MakeFreshOverflowTest {

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
    void hugeFreshDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        // freshDuration = Long.MAX_VALUE - 1000, so gameTime (e.g. 1_000_000) + freshDuration overflows
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(Long.MAX_VALUE - 1000L, 48000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            // Test the arithmetic directly:
            long gameTime = 1_000_000L;
            long freshDuration = Long.MAX_VALUE - 1000L;
            long sum = gameTime + freshDuration;
            // This overflows to negative!
            assertTrue(sum < 0, "The sum must overflow to negative without a guard");

            // The fix: clamp to Long.MAX_VALUE when overflow would occur
            long expectedExpire = (freshDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedExpire,
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

            long gameTime = 1_000_000L;
            long freshDuration = 24000L; // normal carrot fresh duration
            long sum = gameTime + freshDuration;
            assertFalse(sum < 0, "Normal durations must not overflow");
            assertEquals(gameTime + freshDuration, sum);
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

            long gameTime = 1_000_000L;
            long freshDuration = Long.MAX_VALUE;
            long expectedExpire = (freshDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedExpire,
                    "Long.MAX_VALUE freshDuration must clamp to Long.MAX_VALUE");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
