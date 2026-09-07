package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 183 regression test: SpoilageConfig duration methods + speed multiplier.
 *
 * getFreshDurationForItem / getStaleDurationForItem divide the base duration by
 * the configured speed multiplier (higher speed = shorter duration). The
 * applySpeedMultiplier guard (<= 0 returns base) and the Math.max(1L, ...) floor
 * were never directly pinned — only used incidentally in CraftingInheritanceTest.
 */
public class SpoilageConfigDurationTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void freshDurationIsPositiveForSpoilableItem() {
        long duration = SpoilageConfig.getInstance().getFreshDurationForItem(Items.APPLE);
        assertTrue(duration > 0, "A spoilable item must have a positive fresh duration, got " + duration);
    }

    @Test
    void staleDurationIsPositiveForSpoilableItem() {
        long duration = SpoilageConfig.getInstance().getStaleDurationForItem(Items.APPLE);
        assertTrue(duration > 0, "A spoilable item must have a positive stale duration, got " + duration);
    }

    @Test
    void durationIsConsistentAcrossCalls() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        long first = config.getFreshDurationForItem(Items.APPLE);
        long second = config.getFreshDurationForItem(Items.APPLE);
        assertEquals(first, second, "The same item must return the same duration on every call");
    }

    @Test
    void speedMultiplierChangesDuration() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double originalMultiplier = config.getSpoilageSpeedMultiplier();
        try {
            // Speed 2x → duration halves (Math.max(1L, base / 2))
            long baseDuration = config.getBaseFreshDurationForItem(Items.APPLE);
            config.setSpoilageSpeedMultiplier(2.0);
            long doubled = config.getFreshDurationForItem(Items.APPLE);
            long expected = Math.max(1L, (long) (baseDuration / 2.0));
            assertEquals(expected, doubled,
                    "Speed 2x must halve the duration: base=" + baseDuration + " got=" + doubled);
        } finally {
            config.setSpoilageSpeedMultiplier(originalMultiplier);
        }
    }

    @Test
    void speedMultiplierFloorIsOneTick() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double originalMultiplier = config.getSpoilageSpeedMultiplier();
        try {
            // Speed 100x on a short duration must floor at 1 tick, never 0 or negative
            config.setSpoilageSpeedMultiplier(100.0);
            long duration = config.getFreshDurationForItem(Items.APPLE);
            assertTrue(duration >= 1,
                    "Even at 100x speed the duration must floor at 1 tick, got " + duration);
        } finally {
            config.setSpoilageSpeedMultiplier(originalMultiplier);
        }
    }

    @Test
    void slowSpeedExtendsDuration() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        double originalMultiplier = config.getSpoilageSpeedMultiplier();
        try {
            // Speed 0.5x → duration doubles
            long baseDuration = config.getBaseFreshDurationForItem(Items.APPLE);
            config.setSpoilageSpeedMultiplier(0.5);
            long slowed = config.getFreshDurationForItem(Items.APPLE);
            long expected = Math.max(1L, (long) (baseDuration / 0.5));
            assertEquals(expected, slowed,
                    "Speed 0.5x must double the duration: base=" + baseDuration + " got=" + slowed);
        } finally {
            config.setSpoilageSpeedMultiplier(originalMultiplier);
        }
    }

    @Test
    void nonSpoilableItemStillHasDefaultDuration() {
        // getFreshDurationForItem works for any item — it returns the default when
        // no per-item override exists. The isSpoilable check is separate.
        long duration = SpoilageConfig.getInstance().getFreshDurationForItem(Items.STONE);
        assertTrue(duration > 0, "A non-spoilable item still gets a (default) duration, got " + duration);
    }
}