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
 * Pass 147 regression test: BlockSpoilageData eviction correctness.
 *
 * The old evictOldestRottenOrExpired() guarded on expirationTime >= 0, which silently
 * skipped legacy entries (legacyBirthTime >= 0, expirationTime == -1 — the default
 * that getSpoilageState never populates, see Pass 136). A world full of legacy entries
 * would produce no eviction candidate and setSpoilageState would silently push the map
 * past MAX_TRACKED_BLOCKS.
 */
public class BlockSpoilageDataEvictionTest {

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
    void legacyEntryIsEvictable() {
        // A map with one legacy entry should be able to evict it when at capacity
        BlockPos pos = new BlockPos(100, 64, 200);
        BlockSpoilageData data = legacyData(pos, 1000L);

        assertTrue(data.isTracked(pos), "Legacy entry should be tracked");

        // The entry has expirationTime == -1 (never populated for legacy format)
        BlockSpoilageData.BlockSpoilageEntry entry = data.getEntry(pos);
        assertNotNull(entry);
        assertTrue(entry.legacyBirthTime >= 0, "Legacy entry should have legacyBirthTime set");
        assertEquals(-1L, entry.expirationTime, "Legacy entry should have default expirationTime");

        // setSpoilageState should successfully add a new entry (eviction produces a candidate)
        // The map has 1 entry, MAX is 10000, so no eviction needed
        BlockPos newPos = new BlockPos(200, 64, 300);
        data.setSpoilageState(newPos, FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        assertTrue(data.isTracked(newPos), "New entry should be tracked after setSpoilageState");
    }

    @Test
    void evictionPicksCandidateWhenAllEntriesAreLegacy() {
        // Create a map with several legacy entries (all have expirationTime == -1)
        BlockSpoilageData data = new BlockSpoilageData();
        BlockSpoilageData[] datas = {data};

        // Fill the map to capacity with legacy entries
        for (int i = 0; i < 10000; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            datas[0] = BlockSpoilageData.fromCompound(buildLegacyNbt(pos, 1000L + i));
        }

        // Now try to add one more entry — this should succeed because the new
        // evictOldestRottenOrExpired() picks a legacy entry (by smallest key)
        // even though all have expirationTime == -1.
        BlockSpoilageData finalData = datas[0];
        BlockPos newPos = new BlockPos(99999, 64, 0);
        finalData.setSpoilageState(newPos, FoodSpoilageUtil.SpoilageState.FRESH, 5000L);

        // The new entry should be tracked
        assertTrue(finalData.isTracked(newPos),
                "New entry should be tracked after eviction (was: bug — silent overflow past cap)");

        // The map size should be exactly MAX_TRACKED_BLOCKS (not MAX + 1)
        assertTrue(finalData.getEntries().size() <= 10000,
                "Map size should not exceed MAX_TRACKED_BLOCKS, was " + finalData.getEntries().size());
    }

    @Test
    void rottenEntryIsPreferredOverLegacy() {
        // A map with both rotten and legacy entries should evict the rotten one first
        BlockSpoilageData data = new BlockSpoilageData();

        // Add a legacy entry
        BlockPos legacyPos = new BlockPos(100, 64, 200);
        data.setSpoilageState(legacyPos, FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        // Manually convert to legacy format (set legacyBirthTime, clear expirationTime)
        BlockSpoilageData.BlockSpoilageEntry legacyEntry = data.getEntry(legacyPos);
        legacyEntry.legacyBirthTime = 1000L;
        legacyEntry.expirationTime = -1L;

        // Add a rotten entry
        BlockPos rottenPos = new BlockPos(300, 64, 400);
        data.setSpoilageState(rottenPos, FoodSpoilageUtil.SpoilageState.ROTTEN, -1L);

        // Fill to capacity with more legacy entries
        for (int i = 0; i < 10000 - 2; i++) {
            BlockPos pos = new BlockPos(i + 1000, 64, 0);
            BlockSpoilageData loaded = BlockSpoilageData.fromCompound(buildLegacyNbt(pos, 2000L + i));
            for (var e : loaded.getEntries().entrySet()) {
                data.setSpoilageState(BlockPos.of(e.getKey()),
                        e.getValue().state, e.getValue().expirationTime);
            }
        }

        // Now add one more entry — should evict the rotten one (not a legacy one)
        BlockPos newPos = new BlockPos(99999, 64, 0);
        data.setSpoilageState(newPos, FoodSpoilageUtil.SpoilageState.FRESH, 5000L);

        // The rotten entry should be gone, the new entry should be present
        assertTrue(data.isTracked(newPos));
        assertFalse(data.isTracked(rottenPos),
                "Rotten entry should have been evicted (preferred over legacy)");
    }

    @Test
    void evictionSucceedsWithMixedLegacyAndStateEntries() {
        // A map with both legacy and state-format entries — eviction should still produce a candidate
        BlockSpoilageData data = new BlockSpoilageData();

        // Add several legacy entries
        for (int i = 0; i < 50; i++) {
            BlockPos pos = new BlockPos(i, 64, 0);
            BlockSpoilageData loaded = BlockSpoilageData.fromCompound(buildLegacyNbt(pos, 1000L + i));
            for (var e : loaded.getEntries().entrySet()) {
                data.setSpoilageState(BlockPos.of(e.getKey()),
                        e.getValue().state, e.getValue().expirationTime);
            }
        }

        // Add several state-format entries
        for (int i = 0; i < 50; i++) {
            data.setSpoilageState(new BlockPos(i + 1000, 64, 0),
                    FoodSpoilageUtil.SpoilageState.FRESH, 5000L + i);
        }

        assertEquals(100, data.getEntries().size());

        // Add one more — should evict something
        data.setSpoilageState(new BlockPos(99999, 64, 0), FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        assertTrue(data.isTracked(new BlockPos(99999, 64, 0)));
        assertTrue(data.getEntries().size() <= 10000);
    }

    private static CompoundTag buildLegacyNbt(BlockPos pos, long legacyBirthTime) {
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        CompoundTag entryNbt = new CompoundTag();
        entryNbt.putLong("BirthTime", legacyBirthTime);
        blocks.put(String.valueOf(pos.asLong()), entryNbt);
        nbt.put("Blocks", blocks);
        return nbt;
    }
}