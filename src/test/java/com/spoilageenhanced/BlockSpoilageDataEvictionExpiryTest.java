package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1030 regression test: eviction must prefer the entry that is closest to expiring,
 * not the one with the smallest position key.
 *
 * <p>The old evictOldestRottenOrExpired() used the position key (posLong) as the sole
 * tiebreaker for non-ROTTEN entries. That is wrong: a freshly-placed block at a low
 * coordinate can have a far LATER expiration than a stale block at a high coordinate,
 * so the map would evict the fresh one and keep the one about to rot. When the map is
 * at capacity and a new block is registered, the block that was about to go bad is the
 * one that should be dropped — not a brand-new block that still has its whole life
 * ahead of it.</p>
 */
public class BlockSpoilageDataEvictionExpiryTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void closestToExpiringIsEvictedNotSmallestPosition() {
        BlockSpoilageData data = new BlockSpoilageData();

        // A fresh block at a LOW coordinate — far from expiring.
        long freshExp = 100000L;
        data.setSpoilageState(pos(0, 64, 0), FoodSpoilageUtil.SpoilageState.FRESH, freshExp);

        // A stale block at a HIGH coordinate — expiring very soon.
        long staleExp = 10L;
        data.setSpoilageState(pos(1000, 64, 1000), FoodSpoilageUtil.SpoilageState.STALE, staleExp);

        // Fill the rest of the map with legacy entries so the next add forces eviction.
        for (int i = 0; i < 10000 - 2; i++) {
            data.setSpoilageState(pos(i + 2000, 64, 0),
                    FoodSpoilageUtil.SpoilageState.FRESH, 5000L + i);
        }

        // Adding one more must evict the stale block (closest to expiring), not the
        // fresh block at the low coordinate (smallest position key).
        data.setSpoilageState(pos(99999, 64, 0), FoodSpoilageUtil.SpoilageState.FRESH, 5000L);

        assertFalse(data.isTracked(pos(1000, 64, 1000)),
                "The stale block closest to expiring should have been evicted");
        assertTrue(data.isTracked(pos(0, 64, 0)),
                "The fresh block with the smallest position key should NOT have been evicted");
        assertTrue(data.getEntries().size() <= 10000);
    }

    @Test
    void legacyEntriesGoLast() {
        BlockSpoilageData data = new BlockSpoilageData();

        // A state-format entry expiring soon.
        data.setSpoilageState(pos(1000, 64, 1000), FoodSpoilageUtil.SpoilageState.STALE, 10L);

        // A legacy entry (expirationTime == -1) at a low coordinate.
        data.setSpoilageState(pos(0, 64, 0), FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        BlockSpoilageData.BlockSpoilageEntry legacy = data.getEntry(pos(0, 64, 0));
        legacy.legacyBirthTime = 1000L;
        legacy.expirationTime = -1L;

        // Fill the rest.
        for (int i = 0; i < 10000 - 2; i++) {
            data.setSpoilageState(pos(i + 2000, 64, 0),
                    FoodSpoilageUtil.SpoilageState.FRESH, 5000L + i);
        }

        data.setSpoilageState(pos(99999, 64, 0), FoodSpoilageUtil.SpoilageState.FRESH, 5000L);

        assertFalse(data.isTracked(pos(1000, 64, 1000)),
                "The state-format entry closest to expiring should be evicted before the legacy entry");
        assertTrue(data.isTracked(pos(0, 64, 0)),
                "The legacy entry (never expires) should be kept");
    }

    private static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}