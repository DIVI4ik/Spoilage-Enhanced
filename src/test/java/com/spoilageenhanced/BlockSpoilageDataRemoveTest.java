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
 * Pass 248 regression test: BlockSpoilageData.remove.
 *
 * <p>remove(pos) removes the entry if present, does nothing if absent. It does not
 * return a value (void). This test verifies the behavior.</p>
 */
public class BlockSpoilageDataRemoveTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void removeTrackedPosRemovesEntry() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(1, 2, 3);
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        assertTrue(data.isTracked(pos), "Pos must be tracked before remove");

        data.remove(pos);

        assertFalse(data.isTracked(pos), "Pos must not be tracked after remove");
        assertNull(data.getEntry(pos), "Entry must be null after remove");
    }

    @Test
    void removeUntrackedPosIsNoOp() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(1, 2, 3);
        assertFalse(data.isTracked(pos), "Pos must not be tracked initially");

        // Must not throw
        data.remove(pos);

        assertFalse(data.isTracked(pos), "Pos must still not be tracked");
    }

    @Test
    void removeDoesNotAffectOtherEntries() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos1 = new BlockPos(1, 2, 3);
        BlockPos pos2 = new BlockPos(4, 5, 6);
        data.setSpoilageState(pos1, FoodSpoilageUtil.SpoilageState.FRESH, 10000L);
        data.setSpoilageState(pos2, FoodSpoilageUtil.SpoilageState.STALE, 20000L);

        data.remove(pos1);

        assertFalse(data.isTracked(pos1), "pos1 must be removed");
        assertTrue(data.isTracked(pos2), "pos2 must still be tracked");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE,
                data.getEntry(pos2).state, "pos2 state must be unchanged");
    }
}