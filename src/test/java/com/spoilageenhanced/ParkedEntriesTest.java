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
 * Pass 226 regression test: BlockSpoilageData parked-entries lifecycle.
 *
 * <p>The park/takeParked/prunePark mechanism lets a block break that happens in one tick
 * inherit the spoilage state of the block that was just broken — the entry is parked
 * before the drop and reclaimed when the drop is stamped. The 2-tick TTL bounds the
 * window so a second break on the same pos can't overwrite an un-claimed entry.</p>
 */
public class ParkedEntriesTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parkAndTakeReturnsEntry() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(1, 2, 3);
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.STALE, 9000L);

        data.park(pos, entry, 1000L);
        BlockSpoilageData.BlockSpoilageEntry taken = data.takeParked(pos, 1000L);
        assertSame(entry, taken, "takeParked must return the parked entry within the TTL window");
    }

    @Test
    void takeParkedRemovesEntry() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(4, 5, 6);
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.FRESH, 5000L);

        data.park(pos, entry, 1000L);
        data.takeParked(pos, 1000L);
        // Second take must return null — the entry was removed.
        assertNull(data.takeParked(pos, 1000L),
                "takeParked must remove the entry so a second take returns null");
    }

    @Test
    void takeParkedReturnsNullAfterTTL() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(7, 8, 9);
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.STALE, 9000L);

        data.park(pos, entry, 1000L);
        // 3 ticks later — past the 2-tick TTL.
        BlockSpoilageData.BlockSpoilageEntry taken = data.takeParked(pos, 1003L);
        assertNull(taken, "takeParked must return null after the 2-tick TTL expires");
    }

    @Test
    void takeParkedReturnsNullForUnknownPos() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(10, 11, 12);
        assertNull(data.takeParked(pos, 1000L),
                "takeParked must return null for a pos that was never parked");
    }

    @Test
    void parkNullEntryIsNoOp() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(13, 14, 15);
        data.park(pos, null, 1000L);
        assertNull(data.takeParked(pos, 1000L),
                "park(null) must not store anything");
    }

    @Test
    void pruneParkExpiresStaleEntries() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(16, 17, 18);
        BlockSpoilageData.BlockSpoilageEntry entry =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.STALE, 9000L);

        data.park(pos, entry, 1000L);
        // park() calls prunePark internally. Now park another entry at a later time —
        // the first entry should be pruned because it's past the TTL.
        data.park(new BlockPos(19, 20, 21),
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.FRESH, 5000L),
                1003L);
        // The first entry was parked at 1000, now we're at 1003 — 3 ticks later, past TTL.
        assertNull(data.takeParked(pos, 1003L),
                "prunePark must expire entries past the TTL when a new park happens");
    }

    @Test
    void parkOverwritesPreviousEntry() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(22, 23, 24);
        BlockSpoilageData.BlockSpoilageEntry first =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        BlockSpoilageData.BlockSpoilageEntry second =
                new BlockSpoilageData.BlockSpoilageEntry(FoodSpoilageUtil.SpoilageState.STALE, 9000L);

        data.park(pos, first, 1000L);
        data.park(pos, second, 1000L);
        BlockSpoilageData.BlockSpoilageEntry taken = data.takeParked(pos, 1000L);
        assertSame(second, taken,
                "A second park on the same pos must overwrite the first entry");
    }
}
