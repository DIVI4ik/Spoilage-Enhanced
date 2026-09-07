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
 * Pass 135 regression test: BlockSpoilageData.rescaleExpirations overflow guards.
 *
 * The old rescaleExpirations did (long)(elapsed * ratio) with no overflow protection.
 * When /spoilage speed changes the multiplier, ratio can be up to 10,000 (old=100, new=0.01).
 * elapsed/remaining can be up to ~2^63, so the product overflows long silently, wrapping
 * to negative. newElapsed negative → currentTime - newElapsed becomes a FUTURE birth time,
 * resurrecting stale/rotten blocks to FRESH. This test pins the overflow guards.
 *
 * Legacy-format entries (BirthTime) are built via fromCompound because setSpoilageState
 * only creates the state/expiration format.
 */
public class BlockSpoilageDataRescaleTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Builds a BlockSpoilageData holding one legacy-format (BirthTime) entry. */
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
    void rescaleWithLargeRatioDoesNotOverflowLegacyBirthTime() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 1_000_000_000_000L;
        long elapsed = 500_000_000_000L; // 5e11
        BlockSpoilageData data = legacyData(pos, currentTime - elapsed);

        // ratio = 10,000 (old=100, new=0.01) — elapsed * ratio = 5e15, fits in long
        data.rescaleExpirations(currentTime, 10_000.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.legacyBirthTime <= currentTime,
                "legacyBirthTime must not become future time after rescale; got "
                        + entry.legacyBirthTime + " > " + currentTime);
    }

    @Test
    void rescaleWithInfiniteRatioClampsToNever() {
        BlockPos pos = new BlockPos(1, 2, 3);
        long currentTime = 1000L;
        BlockSpoilageData data = legacyData(pos, currentTime - 500L);

        data.rescaleExpirations(currentTime, Double.POSITIVE_INFINITY);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertEquals(Long.MAX_VALUE, entry.legacyBirthTime,
                "Infinity ratio must clamp legacyBirthTime to Long.MAX_VALUE sentinel");
    }

    @Test
    void rescaleWithNaNRatioClampsToNever() {
        BlockPos pos = new BlockPos(1, 2, 3);
        long currentTime = 1000L;
        BlockSpoilageData data = legacyData(pos, currentTime - 500L);

        data.rescaleExpirations(currentTime, Double.NaN);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertEquals(Long.MAX_VALUE, entry.legacyBirthTime,
                "NaN ratio must clamp legacyBirthTime to Long.MAX_VALUE sentinel");
    }

    @Test
    void rescaleWithNegativeRatioDoesNotResurrect() {
        BlockPos pos = new BlockPos(1, 2, 3);
        long currentTime = 1000L;
        BlockSpoilageData data = legacyData(pos, currentTime - 500L);

        data.rescaleExpirations(currentTime, -1.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.legacyBirthTime <= currentTime,
                "Negative ratio must not resurrect block to future birth time; got "
                        + entry.legacyBirthTime + " > " + currentTime);
    }

    @Test
    void rescaleExpirationTimeDoesNotOverflow() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(10, 20, 30);
        long currentTime = 1000L;
        long expireTime = currentTime + 500_000_000_000L; // remaining = 5e11
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.STALE, expireTime);

        // ratio = 10,000 — remaining * ratio = 5e15, fits but exercises the guard
        data.rescaleExpirations(currentTime, 10_000.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.expirationTime >= currentTime,
                "expirationTime must not become past time after rescale; got "
                        + entry.expirationTime + " < " + currentTime);
    }

    @Test
    void rescaleAlreadyExpiredDoesNotResurrect() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(5, 5, 5);
        long currentTime = 1000L;
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.STALE, currentTime - 100L);

        data.rescaleExpirations(currentTime, 2.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.expirationTime <= currentTime,
                "Already expired stale block must not be resurrected; got "
                        + entry.expirationTime + " > " + currentTime);
    }

    @Test
    void rescaleRottenIsNoOp() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(7, 7, 7);
        long currentTime = 1000L;
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.ROTTEN, -1L);

        data.rescaleExpirations(currentTime, 100.0);

        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, entry.state);
        assertEquals(-1L, entry.expirationTime);
    }
}
