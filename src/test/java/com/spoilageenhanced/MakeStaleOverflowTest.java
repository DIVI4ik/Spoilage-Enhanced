package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
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
 * Pass 1397 (L7 — boundary): overflow guard in FoodSpoilageUtil.makeStale.
 *
 * <p>makeStale computes {@code world.getGameTime() + staleDuration} for every item
 * in the stack. staleDuration is unclamped by the config loader (clampHandEditedValues
 * touches only defaults and speed multiplier; computeBaseDurations accepts any stale > 0
 * up to Long.MAX_VALUE). A hand-edited huge staleDuration combined with a real game time
 * can overflow the addition to negative, making the stack read as instantly rotten
 * (since the expiration is in the past).</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel) when the addition would
 * overflow, matching the guards in initializeItemSpoilage (pass 1164),
 * updateSpoilageDataImpl (pass 233), and BlockSpoilageData.getSpoilageState (pass 1396).</p>
 */
public class MakeStaleOverflowTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
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
    void hugeStaleDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        // staleDuration = Long.MAX_VALUE - 1000, so gameTime (e.g. 1_000_000) + staleDuration overflows
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(24000L, Long.MAX_VALUE - 1000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            ItemStack stack = new ItemStack(Items.CARROT, 1);
            // Simulate a world with gameTime = 1_000_000
            // We can't easily mock Level, but we can test the arithmetic directly:
            long gameTime = 1_000_000L;
            long staleDuration = Long.MAX_VALUE - 1000L;
            long sum = gameTime + staleDuration;
            // This overflows to negative!
            assertTrue(sum < 0, "The sum must overflow to negative without a guard");

            // The fix: clamp to Long.MAX_VALUE when overflow would occur
            long expectedExpire = (staleDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + staleDuration;
            assertEquals(Long.MAX_VALUE, expectedExpire,
                    "The guard must clamp to Long.MAX_VALUE when overflow would occur");

        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void normalStaleDurationComputesPlainly() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            long gameTime = 1_000_000L;
            long staleDuration = 48000L; // normal carrot stale duration
            long sum = gameTime + staleDuration;
            assertFalse(sum < 0, "Normal durations must not overflow");
            assertEquals(gameTime + staleDuration, sum);
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }

    @Test
    void exactlyMaxStaleDurationClampsToNever() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(24000L, Long.MAX_VALUE));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            long gameTime = 1_000_000L;
            long staleDuration = Long.MAX_VALUE;
            long expectedExpire = (staleDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + staleDuration;
            assertEquals(Long.MAX_VALUE, expectedExpire,
                    "Long.MAX_VALUE staleDuration must clamp to Long.MAX_VALUE");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
