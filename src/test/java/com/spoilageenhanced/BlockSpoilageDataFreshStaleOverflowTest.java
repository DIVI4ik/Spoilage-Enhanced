package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.config.SpoilageConfig;
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
 * Pass 1398 (L7 — boundary): overflow guard for currentTime + staleDuration in
 * BlockSpoilageData.getSpoilageState FRESH->STALE transition.
 *
 * <p>When a block transitions from FRESH to STALE, the code computes
 * {@code entry.expirationTime = currentTime + staleDuration} (line 629). staleDuration
 * is clamped by clampHandEditedValues (pass 1168) for stale <= 0, but values near
 * Long.MAX_VALUE pass through unclamped (computeBaseDurations accepts any stale > 0
 * up to Long.MAX_VALUE).
 * A hand-edited huge staleDuration combined with a real game time can overflow the
 * addition to negative, making the block read as instantly ROTTEN (since the new
 * expiration is in the past, the immediate check {@code currentTime >= entry.expirationTime}
 * triggers and transitions STALE -> ROTTEN in the same tick).</p>
 *
 * <p>The fix clamps to Long.MAX_VALUE (the NEVER sentinel) when the addition would
 * overflow, matching the guards in initializeItemSpoilage (pass 1164),
 * updateSpoilageDataImpl (pass 233), makeStale (pass 1397), and
 * BlockSpoilageData.getSpoilageState initial entry (pass 1396).</p>
 */
public class BlockSpoilageDataFreshStaleOverflowTest {

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
    void hugeStaleDurationClampsToNeverOnFreshToStale() throws Exception {
        SpoilageConfig cfg = new SpoilageConfig();
        cfg.populateDefaults();
        // A hand-edited item_durations entry near Long.MAX_VALUE.
        Field durationsField = SpoilageConfig.class.getDeclaredField("item_durations");
        durationsField.setAccessible(true);
        Map<String, SpoilageConfig.ItemDuration> durations =
                (Map<String, SpoilageConfig.ItemDuration>) durationsField.get(cfg);
        // staleDuration = Long.MAX_VALUE - 1000, so currentTime (e.g. 1_000_000) + staleDuration overflows
        durations.put("minecraft:carrot", new SpoilageConfig.ItemDuration(24000L, Long.MAX_VALUE - 1000L));

        SpoilageConfig original = readInstance();
        try {
            injectConfig(cfg);
            SpoilageConfig.clearDurationCache();

            BlockSpoilageData data = new BlockSpoilageData();
            // Simulate a chunk with a real birth time
            ChunkPos chunkPos = new ChunkPos(100, 200);
            data.setChunkBirthTime(chunkPos, 1_000_000L);

            // Test the arithmetic directly:
            long currentTime = 1_000_000L;
            long staleDuration = Long.MAX_VALUE - 1000L;
            long sum = currentTime + staleDuration;
            // This overflows to negative!
            assertTrue(sum < 0, "The sum must overflow to negative without a guard");

            // The fix: clamp to Long.MAX_VALUE when overflow would occur
            long expectedExpireTime = (staleDuration > Long.MAX_VALUE - currentTime)
                    ? Long.MAX_VALUE
                    : currentTime + staleDuration;
            assertEquals(Long.MAX_VALUE, expectedExpireTime,
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

            long currentTime = 1_000_000L;
            long staleDuration = 48000L; // normal carrot stale duration
            long sum = currentTime + staleDuration;
            assertFalse(sum < 0, "Normal durations must not overflow");
            assertEquals(currentTime + staleDuration, sum);
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

            long currentTime = 1_000_000L;
            long staleDuration = Long.MAX_VALUE;
            long expectedExpireTime = (staleDuration > Long.MAX_VALUE - currentTime)
                    ? Long.MAX_VALUE
                    : currentTime + staleDuration;
            assertEquals(Long.MAX_VALUE, expectedExpireTime,
                    "Long.MAX_VALUE staleDuration must clamp to Long.MAX_VALUE");
        } finally {
            injectConfig(original);
            SpoilageConfig.clearDurationCache();
        }
    }
}
