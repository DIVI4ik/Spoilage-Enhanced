package com.spoilageenhanced;

import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 218 regression test: FoodSpoilageUtil.extractBestItems.
 *
 * <p>CraftingInheritanceTest covers the padding case (amount > totalTracked). This
 * test pins the best-first ordering and the remaining/extracted split — the
 * mirror of extractWorstItems.</p>
 */
public class ExtractBestItemsTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void extractBestPicksFreshFirst() {
        long now = 1000L;
        // 3 fresh, 1 stale, 1 rotten. Extract 2 — should take the 2 freshest.
        SpoilageData data = new SpoilageData(
                List.of(now + 5000L, now + 3000L, now + 7000L), // fresh (unsorted)
                List.of(now + 500L),                            // stale
                1,                                                // rotten
                1.0);

        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 2);
        SpoilageData extracted = split[1];
        SpoilageData remaining = split[0];

        // Extracted: 2 fresh (the two highest expirations: 7000 and 5000)
        assertEquals(2, extracted.freshExpirations().size(),
                "Best-first extraction must take from fresh first");
        assertTrue(extracted.freshExpirations().contains(now + 7000L),
                "The freshest entry must be extracted");
        assertTrue(extracted.freshExpirations().contains(now + 5000L),
                "The second-freshest entry must be extracted");
        assertEquals(0, extracted.staleExpirations().size(),
                "Stale must not be touched when fresh has enough");
        assertEquals(0, extracted.rottenCount(),
                "Rotten must not be touched when fresh has enough");

        // Remaining: 1 fresh (3000), 1 stale, 1 rotten
        assertEquals(1, remaining.freshExpirations().size());
        assertEquals(1, remaining.staleExpirations().size());
        assertEquals(1, remaining.rottenCount());
    }

    @Test
    void extractBestSpillsIntoStaleWhenFreshExhausted() {
        long now = 1000L;
        // 1 fresh, 2 stale, 0 rotten. Extract 2 — should take 1 fresh + 1 stale.
        SpoilageData data = new SpoilageData(
                List.of(now + 5000L),
                List.of(now + 500L, now + 800L),
                0, 1.0);

        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 2);
        SpoilageData extracted = split[1];

        assertEquals(1, extracted.freshExpirations().size(),
                "Best-first must take the fresh entry first");
        assertEquals(1, extracted.staleExpirations().size(),
                "Best-first must spill into stale when fresh is exhausted");
        assertTrue(extracted.staleExpirations().contains(now + 800L),
                "The freshest stale entry must be extracted");
    }

    @Test
    void extractBestSpillsIntoRottenWhenAllElseExhausted() {
        long now = 1000L;
        // 0 fresh, 0 stale, 3 rotten. Extract 2 — should take 2 rotten.
        SpoilageData data = new SpoilageData(List.of(), List.of(), 3, 1.0);

        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 2);
        SpoilageData extracted = split[1];
        SpoilageData remaining = split[0];

        assertEquals(2, extracted.rottenCount(),
                "Best-first must spill into rotten when fresh and stale are exhausted");
        assertEquals(1, remaining.rottenCount(),
                "Remaining must keep the leftover rotten");
    }

    @Test
    void extractBestPadsWithMaxValueWhenSourceExhausted() {
        long now = 1000L;
        // 1 fresh, 0 stale, 0 rotten. Extract 3 — should pad with 2 Long.MAX_VALUE.
        SpoilageData data = new SpoilageData(List.of(now + 5000L), List.of(), 0, 1.0);

        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 3);
        SpoilageData extracted = split[1];

        assertEquals(3, extracted.freshExpirations().size(),
                "Padding must extend the fresh list to the requested amount");
        assertTrue(extracted.freshExpirations().contains(now + 5000L),
                "The real fresh entry must be present");
        assertEquals(2, extracted.freshExpirations().stream().filter(v -> v == Long.MAX_VALUE).count(),
                "Two padding entries (Long.MAX_VALUE) must be added");
    }

    @Test
    void extractBestZeroAmountReturnsSourceAsRemaining() {
        SpoilageData data = new SpoilageData(List.of(100L), List.of(50L), 1, 1.0);
        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 0);
        assertEquals(data, split[0], "amount=0 must return source as remaining");
        assertEquals(SpoilageData.DEFAULT, split[1], "amount=0 must return DEFAULT as extracted");
    }

    @Test
    void extractBestNullSourceReturnsDefaults() {
        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(null, 5);
        assertEquals(SpoilageData.DEFAULT, split[0], "null source must return DEFAULT as remaining");
        assertEquals(SpoilageData.DEFAULT, split[1], "null source must return DEFAULT as extracted");
    }

    @Test
    void extractBestPreservesSpeedMultiplier() {
        SpoilageData data = new SpoilageData(List.of(100L), List.of(50L), 1, 2.5);
        SpoilageData[] split = FoodSpoilageUtil.extractBestItems(data, 1);
        assertEquals(2.5, split[0].speedMultiplier(), 0.0,
                "Remaining must preserve the source speedMultiplier");
        assertEquals(2.5, split[1].speedMultiplier(), 0.0,
                "Extracted must preserve the source speedMultiplier");
    }
}
