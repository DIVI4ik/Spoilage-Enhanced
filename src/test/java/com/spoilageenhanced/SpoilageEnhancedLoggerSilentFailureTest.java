package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1388 (L1 — silent failure): test SpoilageEnhancedLogger's silent failure patterns.
 *
 * <p>SpoilageEnhancedLogger (SpoilageEnhancedLogger.java:117, :124, :160, :163, :194, :206)
 * has multiple silent-failure catch blocks in the logging infrastructure:</p>
 *
 * <ol>
 *   <li>removeShutdownHook (SpoilageEnhancedLogger.java:117): catches
 *       {@code IllegalStateException} when JVM is already shutting down. Ignored —
 *       nothing to do.</li>
 *   <li>init() outer try-catch (SpoilageEnhancedLogger.java:124): catches {@code Exception}
 *       and prints stack trace. This is the logger initialization itself.</li>
 *   <li>writer thread InterruptedException (SpoilageEnhancedLogger.java:160): catches
 *       {@code InterruptedException}, interrupts current thread, breaks loop.</li>
 *   <li>writer thread Exception (SpoilageEnhancedLogger.java:163): catches {@code Exception}
 *       for writer failures (e.g. ConcurrentModificationException). Reports once to
 *       System.err, sets writerFailureReported flag.</li>
 *   <li>closeWriters writerThread.join (SpoilageEnhancedLogger.java:194): catches
 *       {@code InterruptedException}, increments closeFailures, logs to System.err.</li>
 *   <li>closeWriters writer.flush/close (SpoilageEnhancedLogger.java:206): catches
 *       {@code Exception} for flush/close failures (disk full, file handle gone).
 *       Increments closeFailures, logs to System.err.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: shutdown hook
 * removal ignores IllegalStateException, writer thread handles InterruptedException
 * and generic exceptions with one-time reporting, closeWriters counts failures and
 * reports to System.err.</p>
 */
class SpoilageEnhancedLoggerSilentFailureTest {

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
    void removeShutdownHookIgnoresIllegalStateException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify it catches IllegalStateException and ignores it
        assertTrue(source.contains("try {"),
                "removeShutdownHook must have try block");
        assertTrue(source.contains("} catch (IllegalStateException ignored) {"),
                "removeShutdownHook must catch IllegalStateException");
        assertTrue(source.contains("JVM is already shutting down"),
                "removeShutdownHook must have comment about JVM shutting down");
    }

    @Test
    void initCatchesException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify init catches Exception
        assertTrue(source.contains("} catch (Exception e) {"),
                "init must catch Exception");
        assertTrue(source.contains("e.printStackTrace()"),
                "init must print stack trace");
    }

    @Test
    void writerThreadHandlesInterruptedException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify writer thread handles InterruptedException
        assertTrue(source.contains("} catch (InterruptedException e) {"),
                "writer thread must catch InterruptedException");
        assertTrue(source.contains("Thread.currentThread().interrupt()"),
                "writer thread must re-interrupt");
        assertTrue(source.contains("break;"),
                "writer thread must break loop");
    }

    @Test
    void writerThreadHandlesGenericException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify writer thread handles generic Exception
        assertTrue(source.contains("} catch (Exception e) {"),
                "writer thread must catch Exception");
        assertTrue(source.contains("writerFailureReported"),
                "writer thread must track if failure reported");
        assertTrue(source.contains("System.err.println"),
                "writer thread must report to System.err");
        assertTrue(source.contains("Log writer failed"),
                "writer thread must log failure message");
    }

    @Test
    void closeWritersHandlesJoinInterruptedException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify closeWriters handles InterruptedException on join
        assertTrue(source.contains("try {"),
                "closeWriters must have try block for join");
        assertTrue(source.contains("} catch (InterruptedException e) {"),
                "closeWriters must catch InterruptedException");
        assertTrue(source.contains("closeFailures++"),
                "closeWriters must increment closeFailures");
        assertTrue(source.contains("System.err.println"),
                "closeWriters must report to System.err");
        assertTrue(source.contains("Log writer interrupted during close"),
                "closeWriters must log interrupt message");
    }

    @Test
    void closeWritersHandlesFlushCloseException() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify closeWriters handles flush/close Exception
        assertTrue(source.contains("try {"),
                "closeWriters must have try block for flush/close");
        assertTrue(source.contains("} catch (Exception e) {"),
                "closeWriters must catch Exception");
        assertTrue(source.contains("closeFailures++"),
                "closeWriters must increment closeFailures");
        assertTrue(source.contains("System.err.println"),
                "closeWriters must report to System.err");
        assertTrue(source.contains("Log writer flush/close failed"),
                "closeWriters must log flush/close failure message");
    }

    @Test
    void closeWritersClearsWriters() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify it clears writers map
        assertTrue(source.contains("writers.clear()"),
                "closeWriters must clear writers map");
    }

    @Test
    void closeWritersDoesNotClearShutdownHook() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/SpoilageEnhancedLogger.java"))
                .replace("\r\n", "\n");

        // Verify it does NOT clear shutdownHook (comment explains why)
        assertTrue(source.contains("do NOT clear shutdownHook"),
                "closeWriters must not clear shutdownHook");
    }
}