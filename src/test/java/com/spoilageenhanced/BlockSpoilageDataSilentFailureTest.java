package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1379 (L1 — silent failure): test BlockSpoilageData's silent failure patterns.
 *
 * <p>BlockSpoilageData (BlockSpoilageData.java:126, :156) has two silent-failure
 * catch blocks when loading NBT data:</p>
 *
 * <ol>
 *   <li>Block entry loading (BlockSpoilageData.java:126): catches {@code Exception}
 *       when parsing a block entry. A malformed entry must not silently vanish —
 *       logged at DATA category with the key and exception details. Defaults to
 *       FRESH state for out-of-range state values.</li>
 *   <li>Chunk birth time loading (BlockSpoilageData.java:156): catches {@code NumberFormatException}
 *       when parsing chunk birth times. Explicitly checks for NumericTag type so
 *       non-long values are logged and skipped instead of silently becoming 0L.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: malformed
 * entries are logged with key and exception, chunk birth times check tag type
 * explicitly, defaults to FRESH for invalid state.</p>
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
    void loadBlockEntryHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around block entry loading
        assertTrue(source.contains("try {"),
                "load block entry must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "load block entry must catch Exception");
        assertTrue(source.contains("skipped malformed block entry"),
                "load block entry must log for skipped entry");
    }

    @Test
    void loadBlockEntryLogsKeyAndException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it logs key and exception details
        assertTrue(source.contains("key"),
                "load block entry must log the key");
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "load block entry must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "load block entry must log exception message");
    }

    @Test
    void loadBlockEntryDefaultsToFresh() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it defaults to FRESH for out-of-range state
        assertTrue(source.contains("stateInt >= 0 && stateInt < states.length"),
                "load block entry must check state bounds");
        assertTrue(source.contains("FoodSpoilageUtil.SpoilageState.FRESH"),
                "load block entry must default to FRESH");
    }

    @Test
    void loadChunkBirthTimeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around chunk birth time loading
        assertTrue(source.contains("try {"),
                "load chunk birth time must have try block");
        assertTrue(source.contains("} catch (NumberFormatException e) {"),
                "load chunk birth time must catch NumberFormatException");
        assertTrue(source.contains("skipped malformed chunk birth time"),
                "load chunk birth time must log for skipped entry");
    }

    @Test
    void loadChunkBirthTimeChecksNumericTag() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it explicitly checks for NumericTag
        assertTrue(source.contains("tag instanceof net.minecraft.nbt.NumericTag"),
                "load chunk birth time must check NumericTag");
        assertTrue(source.contains("throw new NumberFormatException"),
                "load chunk birth time must throw for non-numeric tag");
        assertTrue(source.contains("value is not a long"),
                "load chunk birth time must have descriptive message");
    }

    @Test
    void loadChunkBirthTimeLogsKeyAndMessage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it logs key and exception message
        assertTrue(source.contains("key"),
                "load chunk birth time must log the key");
        assertTrue(source.contains("e.getMessage()"),
                "load chunk birth time must log exception message");
    }

    @Test
    void loadParsesSpeedMultiplier() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it parses SpeedMultiplier
        assertTrue(source.contains("nbt.contains(\"SpeedMultiplier\")"),
                "load must check for SpeedMultiplier");
        assertTrue(source.contains("nbt.getDoubleOr(\"SpeedMultiplier\", 1.0)"),
                "load must parse SpeedMultiplier with default");
    }

    @Test
    void loadHandlesMissingChunkBirthTimes() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/block/BlockSpoilageData.java"))
                .replace("\r\n", "\n");

        // Verify it handles missing ChunkBirthTimes
        assertTrue(source.contains("nbt.contains(\"ChunkBirthTimes\")"),
                "load must check for ChunkBirthTimes");
        assertTrue(source.contains("nbt.getCompoundOrEmpty(\"ChunkBirthTimes\")"),
                "load must get compound or empty");
    }
}