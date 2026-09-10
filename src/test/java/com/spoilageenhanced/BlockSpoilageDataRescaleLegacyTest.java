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
 * Pass 1032 regression test: legacy entry rescale must divide elapsed by ratio,
 * not multiply.
 *
 * <p>ratio = old_multiplier / new_multiplier. When the speed multiplier INCREASES
 * (e.g. from 1.0 to 10.0), ratio = 0.1. A block that has aged 1000 ticks at the
 * old speed should now appear to have aged 1000 / 0.1 = 10000 ticks at the new
 * speed — it is effectively 10x older. The old code did elapsed * ratio = 100,
 * making the block appear YOUNGER when the speed increased.</p>
 */
public class BlockSpoilageDataRescaleLegacyTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Builds a BlockSpoilageData with one legacy-format (BirthTime) entry. */
    private static BlockSpoilageData legacyData(BlockPos pos, long legacyBirthTime) {
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        CompoundTag entryNbt = new CompoundTag();
        entryNbt.putLong("BirthTime", legacyBirthTime);
        blocks.put(String.valueOf(pos.asLong()), entryNbt);
        nbt.put("Blocks", blocks);
        return BlockSpoilageData.fromCompound(nbt);
    }

    @Test
    void legacyRescaleIncreasingSpeedMakesBlockOlder() {
        // A legacy block born 1000 ticks ago at speed 1.0
        BlockPos pos = new BlockPos(100, 64, 200);
        BlockSpoilageData data = legacyData(pos, 1000L);

        // Current world time is 2000, so elapsed = 1000 ticks at old speed
        long currentTime = 2000L;

        // Increase speed from 1.0 to 10.0 -> ratio = 1.0 / 10.0 = 0.1
        // The block should now appear 10x older: elapsed = 1000 / 0.1 = 10000
        // New birth time = currentTime - newElapsed = 2000 - 10000 = -8000
        data.rescaleExpirations(currentTime, 0.1);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.legacyBirthTime < 0,
                "Legacy birth time should be in the past (negative relative to currentTime)");
        long newElapsed = currentTime - entry.legacyBirthTime;
        assertEquals(10000L, newElapsed,
                "Increasing speed 10x should make elapsed time 10x larger (divide by ratio)");
    }

    @Test
    void legacyRescaleDecreasingSpeedMakesBlockYounger() {
        // A legacy block born 10000 ticks ago at speed 10.0
        BlockPos pos = new BlockPos(100, 64, 200);
        BlockSpoilageData data = legacyData(pos, -8000L); // born 10000 ticks before currentTime=2000

        // Current world time is 2000, so elapsed = 10000 ticks at old speed
        long currentTime = 2000L;

        // Decrease speed from 10.0 to 1.0 -> ratio = 10.0 / 1.0 = 10.0
        // The block should now appear 10x younger: elapsed = 10000 / 10.0 = 1000
        // New birth time = currentTime - newElapsed = 2000 - 1000 = 1000
        data.rescaleExpirations(currentTime, 10.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.legacyBirthTime > 0,
                "Legacy birth time should be positive (born after world start)");
        long newElapsed = currentTime - entry.legacyBirthTime;
        assertEquals(1000L, newElapsed,
                "Decreasing speed 10x should make elapsed time 10x smaller (divide by ratio)");
    }

    @Test
    void legacyRescaleRatioOneIsNoOp() {
        BlockPos pos = new BlockPos(100, 64, 200);
        BlockSpoilageData data = legacyData(pos, 1000L);
        long currentTime = 2000L;

        // ratio = 1.0 (no speed change) -> elapsed unchanged
        data.rescaleExpirations(currentTime, 1.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertEquals(1000L, entry.legacyBirthTime,
                "ratio=1.0 should leave birth time unchanged");
    }

    @Test
    void stateFormatRescaleStillMultipliesRemaining() {
        // State-format entries (expirationTime) must still multiply remaining by ratio
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 2000L;
        long expireTime = 5000L; // 3000 ticks remaining

        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, expireTime);

        // Increase speed 10x -> ratio = 0.1
        // Remaining should become 3000 * 0.1 = 300
        // New expiration = currentTime + 300 = 2300
        data.rescaleExpirations(currentTime, 0.1);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        long newRemaining = entry.expirationTime - currentTime;
        assertEquals(300L, newRemaining,
                "State-format entries must multiply remaining by ratio");
    }
}