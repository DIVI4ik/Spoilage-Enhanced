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
 * Pass 1036 regression test: the Long.MIN_VALUE never-expires sentinel for legacy entries
 * must NOT be treated as a real birth time.
 *
 * <p>rescaleExpirations writes Long.MIN_VALUE to legacyBirthTime for invalid ratios (the
 * "essentially never expires" sentinel). The legacy-format detection used legacyBirthTime
 * != -1, which Long.MIN_VALUE satisfies, so the sentinel was treated as a real birth time
 * and currentTime - Long.MIN_VALUE overflowed to a huge positive, making the block appear
 * ROTTEN instantly.</p>
 */
public class LegacyNeverSentinelTest {

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
    void neverSentinelSurvivesSaveLoad() {
        // The sentinel must round-trip through NBT so it survives a world save/load
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
        // A legacy entry clamped to the never-expires sentinel must not be re-scaled on the next
        // call (which would compute currentTime - Long.MIN_VALUE and overflow).
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

    @Test
    void neverSentinelIsNotTreatedAsLegacyInRescale() {
        // A legacy entry clamped to the never-expires sentinel must not be re-scaled on the next
        // call (which would compute currentTime - Long.MIN_VALUE and overflow).
        long currentTime = 1000L;
        long posLong = new BlockPos(5, 64, 5).asLong();
        BlockSpoilageData data = legacyData(posLong, Long.MIN_VALUE);

        // Rescale with a valid ratio — the sentinel must be left alone (not treated as legacy)
        data.rescaleExpirations(currentTime, 2.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(new BlockPos(5, 64, 5));
        assertNotNull(entry);
        assertEquals(Long.MIN_VALUE, entry.legacyBirthTime,
                "The never-expires sentinel must not be re-scaled on a valid ratio");
    }
}
