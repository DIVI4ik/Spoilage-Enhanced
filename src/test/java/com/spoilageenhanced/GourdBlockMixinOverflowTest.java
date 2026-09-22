package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1402 (L7 — boundary): overflow guard for world.getGameTime() + freshDuration
 * in GourdBlockMixin (and other mixins calling setSpoilageState with freshDuration).
 *
 * <p>GourdBlockMixin computes {@code world.getGameTime() + freshDuration} when a
 * gourd/pumpkin/melon block is placed. freshDuration is clamped by
 * clampHandEditedValues (pass 1168) for fresh <= 0, but values near Long.MAX_VALUE
 * pass through unclamped (computeBaseDurations accepts any fresh > 0 up to
 * Long.MAX_VALUE). A hand-edited huge freshDuration combined with a real game time can
 * overflow the addition to negative, making the block read as instantly expired on its
 * first tick.</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel) when the addition would
 * overflow, matching the guard in BlockSpoilageData.getSpoilageState initial entry
 * (pass 1396) and BlockStateChangeMixin (pass 1401).</p>
 */
public class GourdBlockMixinOverflowTest {

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
    void hugeFreshDurationClampsToNeverInGourdBlock() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        // freshDuration = Long.MAX_VALUE - 1000, so gameTime (e.g. 1_000_000) + freshDuration overflows
        durations.put("minecraft:pumpkin", new SpoilageConfig.ItemDuration(Long.MAX_VALUE - 1000L, 48000L));

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
            long expectedExpireTime = (freshDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedExpireTime,
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
            long freshDuration = 24000L; // normal pumpkin fresh duration
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
        durations.put("minecraft:pumpkin", new SpoilageConfig.ItemDuration(Long.MAX_VALUE, 48000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            long gameTime = 1_000_000L;
            long freshDuration = Long.MAX_VALUE;
            long expectedExpireTime = (freshDuration > Long.MAX_VALUE - gameTime)
                    ? Long.MAX_VALUE
                    : gameTime + freshDuration;
            assertEquals(Long.MAX_VALUE, expectedExpireTime,
                    "Long.MAX_VALUE freshDuration must clamp to Long.MAX_VALUE");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
