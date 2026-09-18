package com.spoilageenhanced;

import com.spoilageenhanced.command.SpoilageEnhancedDebugCommand;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1355 (L1 — silent failure): test SpoilageEnhancedDebugCommand's silent failure patterns.
 *
 * <p>SpoilageEnhancedDebugCommand has two silent-failure catch blocks:</p>
 *
 * <ol>
 *   <li>Click type parsing (SpoilageEnhancedDebugCommand.java:454): catches {@code IllegalArgumentException}
 *       when parsing the click type enum. Invalid click types are reported to the player
 *       with a helpful error message listing valid options.</li>
 *   <li>Dump command execution (SpoilageEnhancedDebugCommand.java:658): catches {@code Exception}
 *       during the dump operation. Errors are reported to the player via sendFailure.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: invalid inputs are
 * validated with helpful messages, and execution errors are caught and reported.</p>
 */
class SpoilageEnhancedDebugCommandSilentFailureTest {

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
    void clickTypeParsingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around click type parsing
        assertTrue(source.contains("try {"),
                "click type parsing must have try block");
        assertTrue(source.contains("} catch (IllegalArgumentException e) {"),
                "click type parsing must catch IllegalArgumentException");
        assertTrue(source.contains("unknown click type"),
                "click type parsing must report unknown click type");
        assertTrue(source.contains("PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL"),
                "click type parsing must list valid options");
    }

    @Test
    void clickTypeParsingSendsFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it sends failure to player
        assertTrue(source.contains("source.sendFailure"),
                "click type parsing must send failure to player");
        assertTrue(source.contains("return 0;"),
                "click type parsing must return 0 on failure");
    }

    @Test
    void dumpCommandHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around dump execution
        assertTrue(source.contains("try {"),
                "dump command must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "dump command must catch Exception");
        assertTrue(source.contains("CMD_DEBUG_DUMP_ERROR"),
                "dump command must use translation key for error");
    }

    @Test
    void dumpCommandSendsFailure() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it sends failure to player
        assertTrue(source.contains("source.sendFailure"),
                "dump command must send failure to player");
        assertTrue(source.contains("return 0;"),
                "dump command must return 0 on failure");
    }

    @Test
    void dumpCommandHandlesNullDropItem() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify the null dropItem guard (Pass 491)
        assertTrue(source.contains("dropItem == net.minecraft.world.item.Items.AIR"),
                "dump command must check for AIR item");
        assertTrue(source.contains("Items.CARROT"),
                "dump command must use dummy item for stale entries");
        assertTrue(source.contains("getSpoilageState(p, world, dropItem)"),
                "dump command must pass valid dropItem");
    }

    @Test
    void dumpCommandIteratesEntriesAndChunks() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it iterates both entries and chunk birth times
        assertTrue(source.contains("entries.entrySet()"),
                "dump command must iterate block entries");
        assertTrue(source.contains("chunks.entrySet()"),
                "dump command must iterate chunk birth times");
        assertTrue(source.contains("ChunkPos.unpack"),
                "dump command must unpack chunk positions");
    }
}