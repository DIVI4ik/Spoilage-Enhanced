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
 * Pass 247 regression test: BlockSpoilageData.setSpoilageState cap enforcement.
 *
 * <p>When the map is at MAX_TRACKED_BLOCKS, setSpoilageState calls
 * evictOldestRottenOrExpired before adding a new entry. The eviction must always
 * find a candidate so the map never grows past the cap.</p>
 */
public class BlockSpoilageDataCapTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void capIsEnforcedWhenAllEntriesAreFresh() {
        BlockSpoilageData data = new BlockSpoilageData();
        int max = getMaxTrackedBlocks();

        // Fill the map with FRESH entries.
        for (int i = 0; i < max; i++) {
            data.setSpoilageState(new BlockPos(i, 0, 0),
                    FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        }
        assertEquals(max, data.getEntries().size(), "Map must be at capacity");

        // Add one more — must evict one to stay at the cap.
        data.setSpoilageState(new BlockPos(max, 0, 0),
                FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        assertEquals(max, data.getEntries().size(),
                "Map must stay at the cap after adding past capacity");
    }

    @Test
    void capIsEnforcedWhenAllEntriesAreRotten() {
        BlockSpoilageData data = new BlockSpoilageData();
        int max = getMaxTrackedBlocks();

        // Fill the map with ROTTEN entries.
        for (int i = 0; i < max; i++) {
            data.setSpoilageState(new BlockPos(i, 0, 0),
                    FoodSpoilageUtil.SpoilageState.ROTTEN, -1L);
        }
        assertEquals(max, data.getEntries().size(), "Map must be at capacity");

        // Add one more — must evict a ROTTEN entry (the first one found).
        data.setSpoilageState(new BlockPos(max, 0, 0),
                FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        assertEquals(max, data.getEntries().size(),
                "Map must stay at the cap even when all entries are ROTTEN");
    }

    @Test
    void capIsEnforcedWhenAllEntriesAreStale() {
        BlockSpoilageData data = new BlockSpoilageData();
        int max = getMaxTrackedBlocks();

        // Fill the map with STALE entries.
        for (int i = 0; i < max; i++) {
            data.setSpoilageState(new BlockPos(i, 0, 0),
                    FoodSpoilageUtil.SpoilageState.STALE, 10000L);
        }
        assertEquals(max, data.getEntries().size(), "Map must be at capacity");

        // Add one more — must evict one (smallest key) to stay at the cap.
        data.setSpoilageState(new BlockPos(max, 0, 0),
                FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        assertEquals(max, data.getEntries().size(),
                "Map must stay at the cap when all entries are STALE");
    }

    @Test
    void updatingExistingPosDoesNotEvict() {
        BlockSpoilageData data = new BlockSpoilageData();
        int max = getMaxTrackedBlocks();

        // Fill the map.
        for (int i = 0; i < max; i++) {
            data.setSpoilageState(new BlockPos(i, 0, 0),
                    FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        }

        // Update an existing pos — must NOT evict (the pos is already in the map).
        data.setSpoilageState(new BlockPos(0, 0, 0),
                FoodSpoilageUtil.SpoilageState.STALE, 20000L);
        assertEquals(max, data.getEntries().size(),
                "Updating an existing pos must not evict");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE,
                data.getEntry(new BlockPos(0, 0, 0)).state,
                "The updated pos must have the new state");
    }

    private static int getMaxTrackedBlocks() {
        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("MAX_TRACKED_BLOCKS");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
