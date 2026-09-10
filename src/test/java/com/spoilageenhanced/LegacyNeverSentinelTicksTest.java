package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1040 regression test: getTicksUntilNextStage for the Long.MIN_VALUE never-expires
 * sentinel.
 *
 * <p>A legacy entry clamped to the never-expires sentinel has legacyBirthTime == Long.MIN_VALUE
 * and expirationTime == -1 (its default). getTicksUntilNextStage's isLegacy check now excludes
 * the sentinel, so the old state-format path would compute -1 - currentTime and clamp to 0 —
 * a block that should never expire reporting "0 ticks until next stage".</p>
 */
public class LegacyNeverSentinelTicksTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockSpoilageData legacyData(long posLong, long legacyBirthTime) {
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        CompoundTag entryNbt = new CompoundTag();
        entryNbt.putLong("BirthTime", legacyBirthTime);
        blocks.put(String.valueOf(posLong), entryNbt);
        nbt.put("Blocks", blocks);
        return BlockSpoilageData.fromCompound(nbt);
    }

    @Test
    void neverSentinelReportsFreshState() {
        long posLong = new BlockPos(2, 64, 2).asLong();
        BlockSpoilageData data = legacyData(posLong, Long.MIN_VALUE);

        FoodSpoilageUtil.SpoilageState state = data.getSpoilageState(new BlockPos(2, 64, 2), null, null);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, state,
                "The never-expires sentinel must read as FRESH, not ROTTEN");
    }

    @Test
    void neverSentinelSurvivesSaveLoad() {
        long posLong = new BlockPos(3, 64, 3).asLong();
        BlockSpoilageData data = legacyData(posLong, Long.MIN_VALUE);

        CompoundTag nbt = data.toCompound();
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        BlockSpoilageData.BlockSpoilageEntry entry = loaded.getEntry(new BlockPos(3, 64, 3));
        assertNotNull(entry, "Entry must survive save/load");
        assertEquals(Long.MIN_VALUE, entry.legacyBirthTime,
                "The never-expires sentinel must round-trip through NBT");
    }

    @Test
    void neverSentinelIsNotReScaledOnNextCall() {
        long currentTime = 1000L;
        long posLong = new BlockPos(4, 64, 4).asLong();
        BlockSpoilageData data = legacyData(posLong, Long.MIN_VALUE);

        // Rescale with a valid ratio — the sentinel must be left alone (not treated as legacy)
        data.rescaleExpirations(currentTime, 2.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(new BlockPos(4, 64, 4));
        assertNotNull(entry);
        assertEquals(Long.MIN_VALUE, entry.legacyBirthTime,
                "The never-expires sentinel must not be re-scaled on a valid ratio");
    }
}
