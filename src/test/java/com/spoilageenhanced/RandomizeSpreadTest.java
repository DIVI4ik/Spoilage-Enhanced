package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 402 (L13 observed): randomizeSpoilage spread — what a player sees in loot.
 *
 * <p>Scenario: open a loot chest with 3 apples. randomizeSpoilage rolls each item
 * independently: fresh (with random offset), stale (with random offset), or rotten.
 * The player sees a stack with varied freshness — not all items at the same age.
 *
 * What should happen:
 * - each item rolls independently (varied states possible)
 * - fresh offsets are spread within [0, freshDuration)
 * - stale offsets are spread within [0, staleDuration)
 * - the roll thresholds match the config chances
 *
 * The method needs Level for world.getGameTime(), so this test replicates the roll
 * loop (lines 613-627) and pins its distribution behavior.
 */
public class RandomizeSpreadTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    /** Replicates the roll loop from randomizeSpoilage lines 613-627. */
    private static SpoilageData rollAll(int count, long currentTime, long freshDuration, long staleDuration,
                                        float fChance, float sChance, float rChance, RandomSource random) {
        float total = fChance + sChance + rChance;
        if (total <= 0f) {
            fChance = 0.60f;
            sChance = 0.30f;
            rChance = 0.10f;
            total = 1.0f;
        }
        float freshThreshold = fChance / total;
        float staleThreshold = (fChance + sChance) / total;

        java.util.List<Long> freshList = new java.util.ArrayList<>();
        java.util.List<Long> staleList = new java.util.ArrayList<>();
        int rottenCount = 0;
        for (int i = 0; i < count; i++) {
            float roll = random.nextFloat();
            if (roll < freshThreshold) {
                long offset = (long) (random.nextFloat() * freshDuration);
                freshList.add(currentTime + offset);
            } else if (roll < staleThreshold) {
                long offset = (long) (random.nextFloat() * staleDuration);
                staleList.add(currentTime + offset);
            } else {
                rottenCount++;
            }
        }
        return new SpoilageData(freshList, staleList, rottenCount, 1.0);
    }

    @Test
    void freshOffsetsSpreadWithinFreshDuration() {
        // Scenario: 100 loot apples, all roll fresh. The player sees varied "Spoils in" times.
        long now = 1000L;
        long freshDuration = 24000L;
        SpoilageData data = rollAll(100, now, freshDuration, 24000L, 1.0f, 0f, 0f, RandomSource.create());

        assertEquals(100, data.freshExpirations().size(), "all 100 must be fresh");
        assertEquals(0, data.staleExpirations().size());
        assertEquals(0, data.rottenCount());

        Set<Long> distinct = new HashSet<>(data.freshExpirations());
        assertTrue(distinct.size() > 50,
                "100 fresh rolls must produce varied offsets, got " + distinct.size() + " distinct");

        for (long exp : data.freshExpirations()) {
            assertTrue(exp >= now && exp < now + freshDuration,
                    "fresh offset must be within [0, freshDuration), got " + (exp - now));
        }
    }

    @Test
    void staleOffsetsSpreadWithinStaleDuration() {
        // Scenario: 100 loot apples, all roll stale. The player sees varied staleness.
        long now = 1000L;
        long staleDuration = 24000L;
        SpoilageData data = rollAll(100, now, 24000L, staleDuration, 0f, 1.0f, 0f, RandomSource.create());

        assertEquals(0, data.freshExpirations().size());
        assertEquals(100, data.staleExpirations().size(), "all 100 must be stale");
        assertEquals(0, data.rottenCount());

        for (long exp : data.staleExpirations()) {
            assertTrue(exp >= now && exp < now + staleDuration,
                    "stale offset must be within [0, staleDuration), got " + (exp - now));
        }
    }

    @Test
    void rottenChanceProducesRottenItems() {
        // Scenario: 100 loot apples, all roll rotten. The player sees rotten loot.
        SpoilageData data = rollAll(100, 1000L, 24000L, 24000L, 0f, 0f, 1.0f, RandomSource.create());

        assertEquals(0, data.freshExpirations().size());
        assertEquals(0, data.staleExpirations().size());
        assertEquals(100, data.rottenCount(), "all 100 must be rotten");
    }

    @Test
    void defaultChancesProduceMixedStates() {
        // Scenario: 1000 loot apples with default chances (60/30/10). The player sees a mix.
        SpoilageData data = rollAll(1000, 1000L, 24000L, 24000L, 0.60f, 0.30f, 0.10f, RandomSource.create());

        int total = data.freshExpirations().size() + data.staleExpirations().size() + data.rottenCount();
        assertEquals(1000, total, "all 1000 items must be tracked");

        // With 1000 rolls at 60/30/10, each bucket should have at least 5% (50 items)
        assertTrue(data.freshExpirations().size() >= 500,
                "fresh bucket should be ~600, got " + data.freshExpirations().size());
        assertTrue(data.staleExpirations().size() >= 200,
                "stale bucket should be ~300, got " + data.staleExpirations().size());
        assertTrue(data.rottenCount() >= 50,
                "rotten bucket should be ~100, got " + data.rottenCount());
    }

    @Test
    void zeroChancesFallBackToDefaults() {
        // Scenario: config has all-zero chances. The fallback (60/30/10) must kick in.
        SpoilageData data = rollAll(1000, 1000L, 24000L, 24000L, 0f, 0f, 0f, RandomSource.create());

        int total = data.freshExpirations().size() + data.staleExpirations().size() + data.rottenCount();
        assertEquals(1000, total, "all 1000 items must be tracked even with zero chances");
        assertTrue(data.freshExpirations().size() > 0, "fallback must produce fresh items");
        assertTrue(data.staleExpirations().size() > 0, "fallback must produce stale items");
        assertTrue(data.rottenCount() > 0, "fallback must produce rotten items");
    }
}