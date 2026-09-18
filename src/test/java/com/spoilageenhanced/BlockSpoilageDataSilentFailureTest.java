package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1350 (L1 — silent failure): test BlockSpoilageData's silent failure patterns.
 *
 * <p>BlockSpoilageData has two silent-failure catch blocks during NBT loading:</p>
 *
 * <ol>
 *   <li>Block entry loading (BlockSpoilageData.java:126): catches {@code Exception} when
 *       parsing a single block's spoilage data from NBT. A malformed entry is skipped
 *       and logged, rather than crashing the load or silently resurrecting the block
 *       to FRESH state.</li>
 *   <li>Chunk birth time loading (BlockSpoilageData.java:156): catches {@code NumberFormatException}
 *       when parsing chunk birth times. A corrupt value is skipped and logged, rather
 *       than silently becoming 0L (which would make the chunk appear newly inhabited).</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: malformed block
 * entries and chunk birth times are skipped with a log message, the load continues,
 * and the data structure remains consistent.</p>
 *
 * <p>Note: getIntOr/getLongOr return defaults for non-NumericTag values, so a String
 * "State" becomes 0 (FRESH) and a String "Expire" becomes -1L. The catch block handles
 * exceptions like NumberFormatException from Long.parseLong(key) or other issues.
 * The bounds check for state index is handled inline without throwing.</p>
 */
class BlockSpoilageDataSilentFailureTest {

    private static double getSavedMultiplier(BlockSpoilageData data) throws Exception {
        Field f = BlockSpoilageData.class.getDeclaredField("savedMultiplier");
        f.setAccessible(true);
        return f.getDouble(data);
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void loadSkipsEntryWithInvalidKey() throws Exception {
        // Create a BlockSpoilageData with an invalid key (not a long)
        // This triggers NumberFormatException from Long.parseLong(key)
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();

        // Valid entry
        CompoundTag validEntry = new CompoundTag();
        validEntry.putInt("State", FoodSpoilageUtil.SpoilageState.FRESH.ordinal());
        validEntry.putLong("Expire", 1000L);
        blocks.put("12345", validEntry);

        // Invalid key - not a long
        CompoundTag badKeyEntry = new CompoundTag();
        badKeyEntry.putInt("State", FoodSpoilageUtil.SpoilageState.FRESH.ordinal());
        badKeyEntry.putLong("Expire", 2000L);
        blocks.put("not_a_long_key", badKeyEntry);

        nbt.put("Blocks", blocks);
        nbt.putDouble("SpeedMultiplier", 1.0);

        // Load should not throw, should skip malformed entry (catch block catches NumberFormatException)
        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        // Valid entry should be loaded
        assertTrue(loaded.getEntries().containsKey(12345L), "valid entry must be loaded");
        // Invalid key entry should be skipped (catch block catches NumberFormatException)
        assertFalse(loaded.getEntries().containsKey(0L), "invalid key entry must be skipped (key not parsed)");
    }

    @Test
    void loadSkipsMalformedChunkBirthTime() throws Exception {
        BlockSpoilageData data = new BlockSpoilageData();
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        nbt.put("Blocks", blocks);
        nbt.putDouble("SpeedMultiplier", 1.0);

        // Valid chunk birth time
        CompoundTag chunkTimes = new CompoundTag();
        chunkTimes.putLong("11111", 5000L);

        // Malformed chunk birth time - not a NumericTag
        chunkTimes.putString("22222", "not_a_long");

        nbt.put("ChunkBirthTimes", chunkTimes);

        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        // Valid chunk birth time should be loaded
        assertTrue(loaded.getChunkBirthTimes().containsKey(11111L), "valid chunk birth time must be loaded");
        // Malformed chunk birth time should be skipped
        assertFalse(loaded.getChunkBirthTimes().containsKey(22222L), "malformed chunk birth time must be skipped");
    }

    @Test
    void loadHandlesNonNumericTagForChunkBirthTime() throws Exception {
        BlockSpoilageData data = new BlockSpoilageData();
        CompoundTag nbt = new CompoundTag();
        CompoundTag blocks = new CompoundTag();
        nbt.put("Blocks", blocks);
        nbt.putDouble("SpeedMultiplier", 1.0);

        CompoundTag chunkTimes = new CompoundTag();
        // Put a ListTag instead of NumericTag
        chunkTimes.put("33333", new net.minecraft.nbt.ListTag());

        nbt.put("ChunkBirthTimes", chunkTimes);

        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        assertFalse(loaded.getChunkBirthTimes().containsKey(33333L), "non-NumericTag chunk birth time must be skipped");
    }

    @Test
    void loadHandlesEmptyNbt() throws Exception {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Blocks", new CompoundTag());
        nbt.putDouble("SpeedMultiplier", 1.0);

        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        assertTrue(loaded.getEntries().isEmpty(), "empty blocks must produce empty entries");
        assertEquals(1.0, getSavedMultiplier(loaded), "default multiplier must be 1.0");
    }

    @Test
    void loadHandlesMissingBlocksCompound() throws Exception {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("SpeedMultiplier", 1.5);

        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        assertTrue(loaded.getEntries().isEmpty(), "missing blocks must produce empty entries");
        assertEquals(1.5, getSavedMultiplier(loaded), "multiplier must be read from NBT");
    }

    @Test
    void loadHandlesMissingChunkBirthTimes() throws Exception {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Blocks", new CompoundTag());
        nbt.putDouble("SpeedMultiplier", 1.0);

        Method loadMethod = BlockSpoilageData.class.getDeclaredMethod("fromCompound", CompoundTag.class);
        loadMethod.setAccessible(true);
        BlockSpoilageData loaded = (BlockSpoilageData) loadMethod.invoke(null, nbt);

        assertNotNull(loaded, "fromCompound must return a BlockSpoilageData instance");
        assertTrue(loaded.getChunkBirthTimes().isEmpty(), "missing chunk birth times must produce empty map");
    }

    @Test
    void loadSourceHasTryCatchPatternForBlockEntries() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around block entry parsing
        assertTrue(source.contains("try {"),
                "fromCompound must have try block for block entry parsing");
        assertTrue(source.contains("} catch (Exception e) {"),
                "fromCompound must catch Exception for block entry parsing");
        assertTrue(source.contains("BlockSpoilageData: skipped malformed block entry at key"),
                "fromCompound must log for malformed block entry");
    }

    @Test
    void loadSourceHasTryCatchPatternForChunkBirthTimes() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around chunk birth time parsing
        assertTrue(source.contains("try {"),
                "fromCompound must have try block for chunk birth time parsing");
        assertTrue(source.contains("} catch (NumberFormatException e) {"),
                "fromCompound must catch NumberFormatException for chunk birth time parsing");
        assertTrue(source.contains("BlockSpoilageData: skipped malformed chunk birth time at key"),
                "fromCompound must log for malformed chunk birth time");
    }

    @Test
    void loadSourceChecksTagTypeForChunkBirthTimes() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify explicit tag type check (not just getLongOr which would silently default)
        assertTrue(source.contains("if (!(tag instanceof net.minecraft.nbt.NumericTag))"),
                "fromCompound must check tag type explicitly for chunk birth times");
        assertTrue(source.contains("throw new NumberFormatException(\"value is not a long"),
                "fromCompound must throw NumberFormatException for non-NumericTag");
    }
}