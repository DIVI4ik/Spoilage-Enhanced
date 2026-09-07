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
 * Pass 133 regression test: BlockSpoilageData save/load round-trip.
 *
 * The old fromCompound() silently swallowed every parse error and dropped the whole
 * block entry — a corrupt save (out-of-range State ordinal, malformed key) resurrected
 * a stale block to FRESH on world load with no log line. These tests pin the round-trip
 * and the corrupt-entry handling.
 */
public class BlockSpoilageDataSaveLoadTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripPreservesStateAndExpiration() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(12, 64, -8);
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.STALE, 9000L);

        CompoundTag nbt = data.toCompound();
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        BlockSpoilageData.BlockSpoilageEntry entry = loaded.getEntry(pos);
        assertNotNull(entry, "entry must survive a save/load round-trip");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, entry.state);
        assertEquals(9000L, entry.expirationTime);
    }

    @Test
    void roundTripPreservesRottenState() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(1, 2, 3);
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.ROTTEN, -1L);

        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(data.toCompound());
        BlockSpoilageData.BlockSpoilageEntry entry = loaded.getEntry(pos);
        assertNotNull(entry);
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, entry.state);
        assertEquals(-1L, entry.expirationTime);
    }

    @Test
    void outOfRangeStateOrdinalDefaultsToFreshInsteadOfDroppingEntry() {
        // Simulate a corrupt save: a State ordinal beyond the enum's range. The old code
        // threw ArrayIndexOutOfBoundsException (caught and swallowed), dropping the entry
        // entirely so the block came back FRESH. The fix bounds the index and keeps the entry.
        BlockPos pos = new BlockPos(12345, 0, 0);
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        CompoundTag entryNbt = new CompoundTag();
        entryNbt.putInt("State", 99); // out of range for the 3-value enum
        entryNbt.putLong("Expire", 5000L);
        blocks.put(String.valueOf(pos.asLong()), entryNbt);
        nbt.put("Blocks", blocks);

        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);
        BlockSpoilageData.BlockSpoilageEntry entry = loaded.getEntry(pos);
        assertNotNull(entry, "out-of-range State must not drop the entry");
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, entry.state,
                "out-of-range State must default to FRESH, not resurrect a stale block silently");
    }

    @Test
    void malformedKeyDoesNotDropSiblingEntries() {
        // A non-numeric key must not abort the whole loop and lose the valid sibling entry.
        BlockPos goodPos = new BlockPos(999, 0, 0);
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();

        CompoundTag good = new CompoundTag();
        good.putInt("State", FoodSpoilageUtil.SpoilageState.STALE.ordinal());
        good.putLong("Expire", 7000L);
        blocks.put(String.valueOf(goodPos.asLong()), good);

        CompoundTag bad = new CompoundTag();
        bad.putInt("State", 0);
        bad.putLong("Expire", 1000L);
        blocks.put("not-a-number", bad); // Long.parseLong throws here

        nbt.put("Blocks", blocks);

        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);
        BlockSpoilageData.BlockSpoilageEntry goodEntry = loaded.getEntry(goodPos);
        assertNotNull(goodEntry, "valid sibling entry must survive a malformed key");
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, goodEntry.state);
        assertEquals(7000L, goodEntry.expirationTime);
    }

    @Test
    void emptyCompoundYieldsEmptyData() {
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(new CompoundTag());
        assertTrue(loaded.getEntries().isEmpty());
    }

    @Test
    void roundTripPreservesSpeedMultiplier() {
        BlockSpoilageData data = new BlockSpoilageData();
        BlockPos pos = new BlockPos(10, 64, 10);
        data.setSpoilageState(pos, FoodSpoilageUtil.SpoilageState.FRESH, 5000L);
        // Manually set a non-default speed multiplier
        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("savedMultiplier");
            f.setAccessible(true);
            f.set(data, 2.5);
        } catch (Exception e) {
            fail("Failed to set savedMultiplier: " + e.getMessage());
        }

        CompoundTag nbt = data.toCompound();
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("savedMultiplier");
            f.setAccessible(true);
            double loadedMultiplier = f.getDouble(loaded);
            assertEquals(2.5, loadedMultiplier, 0.0,
                    "savedMultiplier must round-trip through NBT");
        } catch (Exception e) {
            fail("Failed to read savedMultiplier: " + e.getMessage());
        }
    }

    @Test
    void roundTripPreservesChunkBirthTimes() {
        BlockSpoilageData data = new BlockSpoilageData();
        // Use reflection to access the private chunkBirthTimes map
        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("chunkBirthTimes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Long> chunkBirthTimes = (java.util.Map<Long, Long>) f.get(data);
            chunkBirthTimes.put(12345L, 987654321L);
            chunkBirthTimes.put(67890L, 123456789L);
        } catch (Exception e) {
            fail("Failed to set chunkBirthTimes: " + e.getMessage());
        }

        CompoundTag nbt = data.toCompound();
        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("chunkBirthTimes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Long> loadedChunkBirthTimes = (java.util.Map<Long, Long>) f.get(loaded);
            assertEquals(2, loadedChunkBirthTimes.size(), "chunkBirthTimes must round-trip");
            assertEquals(987654321L, loadedChunkBirthTimes.get(12345L));
            assertEquals(123456789L, loadedChunkBirthTimes.get(67890L));
        } catch (Exception e) {
            fail("Failed to read chunkBirthTimes: " + e.getMessage());
        }
    }

    @Test
    void corruptChunkBirthTimeValueIsSkippedNotZero() {
        // Pass 440 (Lens 1 — silent failure): getLongOr(key, 0L) silently returned 0L for
        // any non-NumericTag value. A corrupt chunk birth time (e.g. a string tag) would
        // become 0L with no log line, making the chunk appear to have been "born" at tick 0.
        // The fix checks the tag type explicitly and throws NumberFormatException, which is
        // caught and logged. This test pins that a StringTag value is skipped, not stored as 0L.
        CompoundTag nbt = new CompoundTag();
        CompoundTag chunkTimesNbt = new CompoundTag();
        // Valid entry: key parses, value is a long
        chunkTimesNbt.putLong("12345", 987654321L);
        // Corrupt entry: key parses, value is a string (not a NumericTag)
        chunkTimesNbt.putString("67890", "not-a-long");
        nbt.put("ChunkBirthTimes", chunkTimesNbt);

        BlockSpoilageData loaded = BlockSpoilageData.fromCompound(nbt);

        try {
            java.lang.reflect.Field f = BlockSpoilageData.class.getDeclaredField("chunkBirthTimes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Long> loadedChunkBirthTimes = (java.util.Map<Long, Long>) f.get(loaded);
            assertEquals(1, loadedChunkBirthTimes.size(),
                    "Only the valid entry must survive; the corrupt one must be skipped");
            assertEquals(987654321L, loadedChunkBirthTimes.get(12345L),
                    "Valid entry must round-trip");
            assertNull(loadedChunkBirthTimes.get(67890L),
                    "Corrupt entry must NOT be stored as 0L — it must be skipped entirely");
        } catch (Exception e) {
            fail("Failed to read chunkBirthTimes: " + e.getMessage());
        }
    }
}
