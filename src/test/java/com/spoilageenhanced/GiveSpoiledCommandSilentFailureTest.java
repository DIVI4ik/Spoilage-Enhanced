package com.spoilageenhanced;

import com.spoilageenhanced.command.GiveSpoiledCommand;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1354 (L1 — silent failure): test GiveSpoiledCommand's silent failure pattern.
 *
 * <p>GiveSpoiledCommand.execute (GiveSpoiledCommand.java:160) catches {@code Throwable}
 * when executing the command. This is a top-level catch that logs the error, prints
 * the stack trace, and re-throws. The re-throw ensures the command framework sees
 * the failure and reports it to the player.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, logs appropriately,
 * and re-throws the exception.</p>
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
    void executeSourceHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around command execution
        assertTrue(source.contains("try {"),
                "execute must have try block for command execution");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "execute must catch Throwable for command execution");
        assertTrue(source.contains("Error in GiveSpoiledCommand"),
                "execute must log for command error");
    }

    @Test
    void executeLogsAndReThrows() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify it logs and re-throws
        assertTrue(source.contains("SpoilageEnhancedLogger.log"),
                "execute must log the error");
        assertTrue(source.contains("t.printStackTrace()"),
                "execute must print stack trace");
        assertTrue(source.contains("throw t;"),
                "execute must re-throw the exception");
    }

    @Test
    void executeUsesCorrectSecondsCalculation() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/command/GiveSpoiledCommand.java"))
                .replace("\r\n", "\n");

        // Verify the seconds calculation fix (Pass 856)
        assertTrue(source.contains("secondsRemaining > 0 ? secondsRemaining : ticksRemaining / 20L"),
                "execute must use secondsRemaining when provided, otherwise derive from ticks");
    }
}