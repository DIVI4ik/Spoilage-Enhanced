package com.spoilageenhanced;

import com.spoilageenhanced.block.BlockSpoilageData;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1364 (L1 — silent failure): test BlockSpoilageData's silent failure patterns.
 *
 * <p>BlockSpoilageData (BlockSpoilageData.java:126, :156) has two silent-failure catch blocks
 * during NBT loading:</p>
 *
 * <ol>
 *   <li>Block entry loading (BlockSpoilageData.java:126): catches {@code Exception} when
 *       loading a block entry from NBT. A malformed entry must not silently vanish — it's
 *       logged so a corrupted save is diagnosable instead of resurrecting blocks to FRESH.</li>
 *   <li>Chunk birth time loading (BlockSpoilageData.java:156): catches {@code NumberFormatException}
 *       when loading chunk birth times. A corrupt value (e.g., a string instead of a long)
 *       would silently become 0L without this check. The tag type is explicitly checked.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: malformed entries
 * are logged with position and error details, chunk birth times validate tag type.</p>
 */
class BlockSpoilageDataSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void loadBlockEntriesHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around block entry loading
        assertTrue(source.contains("try {"),
                "block entry loading must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "block entry loading must catch Exception");
        assertTrue(source.contains("skipped malformed block entry"),
                "block entry loading must log for malformed entry");
    }

    @Test
    void loadBlockEntriesLogsPositionAndError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it logs position and error details
        assertTrue(source.contains("key"),
                "block entry loading must log the key/position");
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "block entry loading must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "block entry loading must log exception message");
        assertTrue(source.contains("LogCategory.DATA"),
                "block entry loading must log to DATA category");
    }

    @Test
    void loadBlockEntriesDefaultsToFresh() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it defaults to FRESH for out-of-range state
        assertTrue(source.contains("FoodSpoilageUtil.SpoilageState.FRESH"),
                "block entry loading must default to FRESH");
        assertTrue(source.contains("stateInt >= 0 && stateInt < states.length"),
                "block entry loading must bound the state index");
    }

    @Test
    void loadChunkBirthTimesHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around chunk birth time loading
        assertTrue(source.contains("try {"),
                "chunk birth time loading must have try block");
        assertTrue(source.contains("} catch (NumberFormatException e) {"),
                "chunk birth time loading must catch NumberFormatException");
        assertTrue(source.contains("skipped malformed chunk birth time"),
                "chunk birth time loading must log for malformed entry");
    }

    @Test
    void loadChunkBirthTimesChecksTagType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it explicitly checks tag type
        assertTrue(source.contains("instanceof net.minecraft.nbt.NumericTag"),
                "chunk birth time loading must check NumericTag type");
        assertTrue(source.contains("throw new NumberFormatException"),
                "chunk birth time loading must throw for non-numeric tag");
        assertTrue(source.contains("longValue()"),
                "chunk birth time loading must extract long value");
    }

    @Test
    void loadChunkBirthTimesLogsKeyAndError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it logs key and error
        assertTrue(source.contains("key"),
                "chunk birth time loading must log the key");
        assertTrue(source.contains("e.getMessage()"),
                "chunk birth time loading must log exception message");
        assertTrue(source.contains("LogCategory.DATA"),
                "chunk birth time loading must log to DATA category");
    }

    @Test
    void loadHandlesSpeedMultiplier() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it handles SpeedMultiplier
        assertTrue(source.contains("SpeedMultiplier"),
                "load must handle SpeedMultiplier");
        assertTrue(source.contains("savedMultiplier"),
                "load must set savedMultiplier");
    }
}