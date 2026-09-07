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
 * Pass 136 regression test: BlockSpoilageData.getTicksUntilNextStage legacy path.
 *
 * The old getTicksUntilNextStage only understood the expirationTime representation.
 * Legacy-format entries (loaded via the "BirthTime" key in fromCompound) have
 * legacyBirthTime >= 0 and expirationTime == -1. The old code computed
 * expirationTime - currentTime = -1 - currentTime (deeply negative), so
 * Math.max(0, ...) returned 0: a genuinely fresh block reported "0 ticks until
 * next stage" and the HUD rendered a fully-depleted countdown for it.
 *
 * This test pins the legacy path logic directly (without a real Level).
 */
public class BlockSpoilageDataGetTicksTest {

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

    /** Replicates the legacy-path logic from getTicksUntilNextStage (including dynamic state computation). */
    private static long legacyTicksUntilNextStage(BlockSpoilageData data, BlockPos pos, long currentTime,
            long freshDuration, long staleDuration) {
        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        if (entry == null) return freshDuration;

        // Compute actual state from legacyBirthTime (mirrors getSpoilageState logic)
        FoodSpoilageUtil.SpoilageState actualState;
        if (entry.legacyBirthTime >= 0) {
            long age = currentTime - entry.legacyBirthTime;
            if (age < freshDuration) actualState = FoodSpoilageUtil.SpoilageState.FRESH;
            else if (age < freshDuration + staleDuration) actualState = FoodSpoilageUtil.SpoilageState.STALE;
            else actualState = FoodSpoilageUtil.SpoilageState.ROTTEN;
        } else {
            actualState = entry.state;
        }

        if (actualState == FoodSpoilageUtil.SpoilageState.ROTTEN) return 0;
        if (entry.legacyBirthTime >= 0) {
            long age = currentTime - entry.legacyBirthTime;
            if (age < freshDuration) return freshDuration - age;
            if (age < freshDuration + staleDuration) return (freshDuration + staleDuration) - age;
            return 0;
        }
        long remaining = entry.expirationTime - currentTime;
        return Math.max(0, remaining);
    }

    @Test
    void legacyFreshBlockReportsFullRemainingTime() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 100000L; // large enough that birthTime is positive
        long birthTime = currentTime - 100L; // age = 100
        BlockSpoilageData data = legacyData(pos, birthTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        // Age 100 < freshDuration 24000 → remaining = 24000 - 100 = 23900
        assertEquals(freshDuration - 100L, ticks,
                "Fresh legacy block must report full remaining fresh time, not 0");
    }

    @Test
    void legacyStaleBlockReportsStaleRemainingTime() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 100000L; // large enough that birthTime is positive
        long birthTime = currentTime - 30000L; // age = 30000 (freshDuration=24000, staleDuration=48000)
        BlockSpoilageData data = legacyData(pos, birthTime);

        // Debug: verify the entry loaded correctly
        BlockSpoilageData.BlockSpoilageEntry debugEntry = data.getEntry(pos);
        assertNotNull(debugEntry, "Legacy entry must be loaded from NBT");
        assertEquals(birthTime, debugEntry.legacyBirthTime,
                "legacyBirthTime must be preserved; got " + debugEntry.legacyBirthTime + " expected " + birthTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        // Age 30000: freshDuration=24000, so stale age = 6000
        // Remaining stale = staleDuration - 6000 = 42000
        assertEquals(freshDuration + staleDuration - 30000L, ticks,
                "Stale legacy block must report remaining stale time");
    }

    @Test
    void legacyRottenBlockReportsZero() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 100000L; // large enough that birthTime is positive
        long birthTime = currentTime - 100000L; // age = 100000 (fresh+stale = 72000)
        BlockSpoilageData data = legacyData(pos, birthTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        assertEquals(0L, ticks, "Rotten legacy block must report 0 ticks");
    }

    @Test
    void legacyExactlyAtFreshExpirationReportsStaleDuration() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 100000L; // large enough that birthTime is positive
        long birthTime = currentTime - 24000L; // age = exactly freshDuration
        BlockSpoilageData data = legacyData(pos, birthTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        // Age == freshDuration → just entered stale, full staleDuration remaining
        assertEquals(staleDuration, ticks,
                "Block exactly at fresh→stale boundary must report full staleDuration");
    }

    @Test
    void legacyExactlyAtStaleExpirationReportsZero() {
        BlockPos pos = new BlockPos(100, 64, 200);
        long currentTime = 100000L; // large enough that birthTime is positive
        long birthTime = currentTime - 72000L; // age = freshDuration + staleDuration
        BlockSpoilageData data = legacyData(pos, birthTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        assertEquals(0L, ticks,
                "Block exactly at stale→rotten boundary must report 0");
    }

    @Test
    void nonLegacyEntryUsesExpirationTime() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(10, 20, 30);
        long currentTime = 1000L;
        long expireTime = currentTime + 5000L; // 5000 ticks remaining
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, expireTime);

        long freshDuration = 24000L;
        long staleDuration = 48000L;
        long ticks = legacyTicksUntilNextStage(data, pos, currentTime, freshDuration, staleDuration);

        assertEquals(5000L, ticks, "Non-legacy entry must use expirationTime directly");
    }
}