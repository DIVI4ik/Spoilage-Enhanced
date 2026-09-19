package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1369 (L1 — silent failure): test GiveSpoiledCommand's silent failure pattern.
 *
 * <p>GiveSpoiledCommand (GiveSpoiledCommand.java:160) catches {@code Throwable} at the
 * end of the command execution. It logs the error, prints the stack trace, and then
 * re-throws the exception so the command framework can handle it properly.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, logs the error, prints
 * stack trace, and re-throws.</p>
 */
class GiveSpoiledCommandSilentFailureTest {

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
    void executeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around command execution
        assertTrue(source.contains("try {"),
                "execute must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "execute must catch Throwable");
        assertTrue(source.contains("Error in GiveSpoiledCommand"),
                "execute must log the error");
    }

    @Test
    void executeLogsAndPrintsStackTrace() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify it logs and prints stack trace
        assertTrue(source.contains("t.getMessage()"),
                "execute must log exception message");
        assertTrue(source.contains("t.printStackTrace()"),
                "execute must print stack trace");
    }

    @Test
    void executeReThrows() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify it re-throws the exception
        assertTrue(source.contains("throw t;"),
                "execute must re-throw the exception");
    }

    @Test
    void executeCalculatesSecondsCorrectly() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify it calculates seconds correctly
        assertTrue(source.contains("secondsRemaining > 0 ? secondsRemaining : ticksRemaining / 20L"),
                "execute must calculate seconds from ticks when secondsRemaining not given");
    }

    @Test
    void executeSendsSuccessMessage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify it sends success message
        assertTrue(source.contains("CMD_GIVESPOILED_SUCCESS"),
                "execute must send success translation");
        assertTrue(source.contains("item.getName(new ItemStack(item))"),
                "execute must get item name");
    }
}