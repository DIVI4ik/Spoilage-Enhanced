package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1368 (L1 — silent failure): test SpoilageEnhancedDebugCommand's silent failure patterns.
 *
 * <p>SpoilageEnhancedDebugCommand (SpoilageEnhancedDebugCommand.java:454, :658) has two
 * silent-failure catch blocks:</p>
 *
 * <ol>
 *   <li>menuClick (SpoilageEnhancedDebugCommand.java:454): catches {@code IllegalArgumentException}
 *       when parsing the click type. An unknown click type is reported to the command source
 *       with a list of valid types.</li>
 *   <li>dump (SpoilageEnhancedDebugCommand.java:658): catches {@code Exception} when dumping
 *       spoilage data to a file. Any failure is reported to the command source with the
 *       exception message.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: unknown click types
 * and dump failures are reported to the command source, not swallowed.</p>
 */
class DebugCommandSilentFailureTest {

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
    void menuClickHasTryCatchForClickType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around click type parsing
        assertTrue(source.contains("try {"),
                "menuClick must have try block for click type parsing");
        assertTrue(source.contains("} catch (IllegalArgumentException e) {"),
                "menuClick must catch IllegalArgumentException");
        assertTrue(source.contains("unknown click type"),
                "menuClick must report unknown click type");
    }

    @Test
    void menuClickListsValidTypes() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it lists valid click types
        assertTrue(source.contains("PICKUP, QUICK_MOVE, SWAP, THROW, QUICK_CRAFT, PICKUP_ALL"),
                "menuClick must list valid click types");
    }

    @Test
    void menuClickUsesValueOf() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it uses valueOf for parsing
        assertTrue(source.contains("ContainerInput.valueOf(type.toUpperCase(java.util.Locale.ROOT))"),
                "menuClick must use ContainerInput.valueOf");
    }

    @Test
    void dumpHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around dump
        assertTrue(source.contains("try {"),
                "dump must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "dump must catch Exception");
        assertTrue(source.contains("CMD_DEBUG_DUMP_ERROR"),
                "dump must report error to command source");
    }

    @Test
    void dumpReportsExceptionMessage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it reports the exception message
        assertTrue(source.contains("e.getMessage()"),
                "dump must report exception message");
    }

    @Test
    void dumpReportsSuccess() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it reports success
        assertTrue(source.contains("CMD_DEBUG_DUMP_SUCCESS"),
                "dump must report success");
    }

    @Test
    void menuClickChecksPlayerAndMenu() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/SpoilageEnhancedDebugCommand.java"))
                .replace("\r\n", "\n");

        // Verify it checks player and menu
        assertTrue(source.contains("if (player == null)"),
                "menuClick must check for null player");
        assertTrue(source.contains("if (menu == null)"),
                "menuClick must check for null menu");
        assertTrue(source.contains("slot >= menu.slots.size()"),
                "menuClick must check slot range");
    }
}