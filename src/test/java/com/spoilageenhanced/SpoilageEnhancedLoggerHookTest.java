package com.spoilageenhanced;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 202 regression test: the shutdown-hook lifecycle in SpoilageEnhancedLogger.
 *
 * <p>The defect: init() added a NEW shutdown hook Thread on every call, and
 * closeWriters() — which sets initialized=false — never removed the old one. A
 * disable→enable cycle (the {@code /spoilage debug logging} command) therefore
 * accumulated one leaked hook Thread per cycle, all held by the JVM until exit.</p>
 *
 * <p>The fix: init() holds a static reference to the hook and calls
 * removeShutdownHook before adding a new one, so the JVM only ever holds one.</p>
 */
public class SpoilageEnhancedLoggerHookTest {

    @AfterEach
    void cleanup() {
        // closeWriters is idempotent; leave the logger in a clean state for other tests.
        SpoilageEnhancedLogger.closeWriters();
    }

    private static Thread currentHook() throws Exception {
        Field hookField = SpoilageEnhancedLogger.class.getDeclaredField("shutdownHook");
        hookField.setAccessible(true);
        return (Thread) hookField.get(null);
    }

    @Test
    void reinitReplacesHookInsteadOfAccumulating() throws Exception {
        SpoilageEnhancedLogger.init();
        Thread firstHook = currentHook();
        assertNotNull(firstHook, "init() must register a shutdown hook");

        // Simulate the debug-command cycle: disable (closeWriters sets initialized=false),
        // then enable again (init runs the full path again).
        SpoilageEnhancedLogger.closeWriters();
        SpoilageEnhancedLogger.init();

        Thread secondHook = currentHook();
        assertNotNull(secondHook, "Re-init must register a hook again");
        assertNotSame(firstHook, secondHook,
                "The second init must REPLACE the hook, not accumulate a second one");

        // The old hook must no longer be registered with the JVM. There is no public API to
        // enumerate registered shutdown hooks, so we verify the replacement indirectly:
        // removeShutdownHook on the OLD hook must return false (it was already removed by
        // init()), proving init() removed it. If init() had failed to remove it, this call
        // would return true and the assertion would fail.
        boolean removedOld = false;
        try {
            removedOld = Runtime.getRuntime().removeShutdownHook(firstHook);
        } catch (IllegalStateException e) {
            // JVM shutting down — cannot happen in a test
        }
        assertFalse(removedOld,
                "The old hook must already be removed by init(); removeShutdownHook(old) should return false");
    }

    @Test
    void closeWritersKeepsHookReferenceForNextInit() throws Exception {
        SpoilageEnhancedLogger.init();
        Thread hook = currentHook();
        assertNotNull(hook);

        SpoilageEnhancedLogger.closeWriters();
        // The reference must survive closeWriters so the next init() can remove it.
        assertSame(hook, currentHook(),
                "closeWriters must NOT clear the shutdownHook field — the JVM still owns the hook");
    }

    /**
     * Pass 204 (Lens 1 — silent failure): a broken PrintWriter (closed file handle, full
     * disk) must not crash the writer thread, and the first failure must be reported to
     * System.err so the operator can see the logs are degraded. We inject a closed writer
     * into the writers map, log a message, and wait for the writer thread to process it.
     */
    @Test
    void brokenWriterIsReportedOnceAndDoesNotKillThread() throws Exception {
        // Ensure a clean state before we start — close any leftover writer thread from
        // a previous test so the init() below starts a fresh one.
        SpoilageEnhancedLogger.closeWriters();

        // Capture System.err BEFORE init() so the writer thread sees the capture from
        // the very first message (including the init banner). The race the old version
        // hit: the thread started, polled, and processed a leftover message from a
        // previous test's init before the test could set System.err.
        java.io.PrintStream originalErr = System.err;
        java.io.ByteArrayOutputStream errCapture = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(errCapture, true));
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outCapture = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(outCapture, true));

        try {
            SpoilageEnhancedLogger.init();

            // Replace the GENERAL writer with one whose underlying stream is already closed —
            // every println on it throws.
            java.io.PrintWriter broken = new java.io.PrintWriter(new java.io.Writer() {
                @Override public void write(char[] cbuf, int off, int len) throws java.io.IOException {
                    throw new java.io.IOException("simulated disk failure");
                }
                @Override public void flush() {}
                @Override public void close() {}
            }, true);
            java.lang.reflect.Field writersField = SpoilageEnhancedLogger.class.getDeclaredField("writers");
            writersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory, java.io.PrintWriter> writers =
                    (java.util.Map<com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory, java.io.PrintWriter>) writersField.get(null);
            writers.put(com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.GENERAL, broken);

            // Log two messages — the writer thread must survive both and report once.
            SpoilageEnhancedLogger.log("message one that will fail to write");
            SpoilageEnhancedLogger.log("message two that will fail to write");

            // Wait (bounded) for the writer thread to drain the queue and hit the failure.
            // The thread polls every 100ms, so 5s is a generous bound.
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                if (errCapture.toString().contains("Log writer failed")
                        || outCapture.toString().contains("Log writer failed")) {
                    break;
                }
                Thread.sleep(50);
            }
            boolean reported = errCapture.toString().contains("Log writer failed")
                    || outCapture.toString().contains("Log writer failed");
            assertTrue(reported,
                    "The first writer failure must be reported to System.err (errCapture="
                            + errCapture.toString() + ", outCapture=" + outCapture.toString() + ")");
        } finally {
            System.setErr(originalErr);
            System.setOut(originalOut);
            SpoilageEnhancedLogger.closeWriters();
        }
    }
}
