package com.spoilageenhanced.util;

import com.spoilageenhanced.config.SpoilageConfig;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpoilageEnhancedLogger {
    public enum LogCategory {
        GENERAL("general"),
        HUD("hud"),
        DATA("data"),
        EVENTS("events"),
        CHUNKS("chunks"),
        NETWORK("network"),
        TRACE("trace");

        public final String filename;
        LogCategory(String name) { this.filename = name + ".log"; }
    }

    private static final Map<LogCategory, PrintWriter> writers = new EnumMap<>(LogCategory.class);
    // Pass 626 (Lens 1 — silent failure): counts close/flush failures across calls. On the
    // next init() we log a WARNING with the accumulated count so operators can see that the
    // previous session lost log data — without this, an InterruptedException during join or
    // a flush throw during shutdown silently truncates the on-disk log with no signal.
    private static volatile int closeFailures = 0;
    // Pass 678 (Lens 1 — silent failure): logQueue.offer() silently drops messages when the
    // bounded queue is full (10000 entries). Without a counter, operators cannot tell that
    // log data is being lost. Track drops and surface them on init() like closeFailures.
    private static volatile int droppedMessages = 0;
    private static File logDir;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static boolean initialized = false;

    // Async logging queue - bounded to prevent OOM under heavy logging
    private static final BlockingQueue<LogEntry> logQueue = new ArrayBlockingQueue<>(10000);
    private static Thread writerThread;
    private static final AtomicBoolean writerRunning = new AtomicBoolean(false);
    // Pass 202: tracked so init() can replace the previous hook instead of leaking it.
    private static Thread shutdownHook;

    private record LogEntry(LogCategory category, String message) {}

    public static synchronized void init() {
        // Pass 626 (Lens 1): if the previous session lost log data (writer thread was
        // interrupted or flush/close threw), surface it now so operators see the gap.
        if (closeFailures > 0) {
            System.err.println("[SpoilageEnhanced] WARNING: previous log session lost data "
                    + "(closeFailures=" + closeFailures + "). Some log entries may be missing.");
            closeFailures = 0;
        }
        if (droppedMessages > 0) {
            System.err.println("[SpoilageEnhanced] WARNING: previous session dropped " + droppedMessages
                    + " log messages because the async queue was full (10000 entries).");
            droppedMessages = 0;
        }
        // Pass 94: always refresh the static flag cache, even if we early-return. A config
        // reload that disabled logging should still update cachedTraceEnabled.
        refreshConfigCache();
        if (!SpoilageConfig.getInstance().enable_logging) {
            return;
        }
        // Both the platform entrypoint and SpoilageEnhancedCommon call init(); without this
        // the second call wipes the freshly written files and leaks the open writers.
        if (initialized) {
            return;
        }

        try {
            logDir = new File(SpoilageEnhancedPlatform.getGameDir().toFile(), "spoilage_enhanced_logs");
            if (logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            f.delete();
                        }
                    }
                }
            } else {
                logDir.mkdirs();
            }

            for (LogCategory cat : LogCategory.values()) {
                File logFile = new File(logDir, cat.filename);
                writers.put(cat, new PrintWriter(new FileWriter(logFile, false), true));
            }
            initialized = true;
            refreshConfigCache(); // Pass 94: populate the static flag cache

            // Start async writer thread
            startWriterThread();

            // Pass 202 (Lens 1 — silent failure): the previous code added a new shutdown
            // hook on every init() — closeWriters() sets initialized=false but never removed
            // the hook, so a debug command cycle of `disable → enable` accumulated one
            // extra Thread per cycle. The hook calls closeWriters which is idempotent
            // (writers empty by then), so the practical effect was just leaked Thread
            // references held by the JVM until exit, when they all ran (and no-op'd). Now
            // we hold a static reference to the hook and removeShutdownHook before adding
            // a new one, so the JVM only ever holds one at a time.
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM is already shutting down; nothing to do.
                }
            }
            shutdownHook = new Thread(SpoilageEnhancedLogger::closeWriters, "SpoilageEnhancedLogger-Shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            log(LogCategory.GENERAL, "--- SpoilageEnhanced Logging Session Started ---");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startWriterThread() {
        writerRunning.set(true);
        writerThread = new Thread(() -> {
            boolean writerFailureReported = false;
            while (writerRunning.get() || !logQueue.isEmpty()) {
                try {
                    LogEntry entry = logQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        PrintWriter writer = writers.get(entry.category());
                        if (writer != null) {
                            String time = LocalDateTime.now().format(formatter);
                            writer.println(time + " " + entry.message());
                            // Pass 204 (Lens 1 — silent failure): PrintWriter NEVER throws on
                            // an I/O failure — it swallows the IOException internally and sets
                            // an error flag (its documented contract; see the PrintWriter
                            // class javadoc: "methods in this class never throw I/O
                            // exceptions"). The old catch below was therefore dead code for
                            // the actual failure mode: a full disk or an invalidated file
                            // handle dropped every log line with no indication anywhere.
                            // checkError() is the only way to see it. Report the first
                            // failure per session to System.err so the operator can see the
                            // logs are degraded instead of trusting a file that silently
                            // stopped growing.
                            if (writer.checkError() && !writerFailureReported) {
                                writerFailureReported = true;
                                System.err.println("[Spoilage Enhanced] Log writer failed (logging is "
                                        + "degraded, entries may be lost) — category "
                                        + entry.category().filename);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Not dead code for every path (a ConcurrentModificationException on the
                    // writers map would land here), but the PrintWriter failure mode is
                    // handled by checkError() above.
                    if (!writerFailureReported) {
                        writerFailureReported = true;
                        System.err.println("[Spoilage Enhanced] Log writer failed (logging is now "
                                + "degraded, entries may be lost): " + e);
                    }
                }
            }
            // Flush remaining entries
            LogEntry entry;
            while ((entry = logQueue.poll()) != null) {
                PrintWriter writer = writers.get(entry.category());
                if (writer != null) {
                    String time = LocalDateTime.now().format(formatter);
                    writer.println(time + " " + entry.message());
                }
            }
        }, "SpoilageEnhancedLogger-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public static synchronized void closeWriters() {
        writerRunning.set(false);
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(2000); // Wait up to 2 seconds for flush
            } catch (InterruptedException ignored) {
                // Thread was interrupted while waiting for the writer to flush. The writer
                // may have lost queued entries; record the loss so the next init() can
                // surface it. Without this, a forced shutdown truncates the log silently.
                closeFailures++;
            }
        }
        for (PrintWriter writer : writers.values()) {
            try {
                writer.flush();
                writer.close();
            } catch (Exception ignored) {
                // Flush or close failed (disk full, file handle gone, etc). Count it so
                // the next init() can warn the operator instead of leaving them wondering
                // why the log is truncated.
                closeFailures++;
            }
        }
        writers.clear();
        // Pass 202: do NOT clear shutdownHook here — it points at closeWriters, so the JVM
        // still owns it and will run it on exit. Clearing the reference would prevent the
        // next init() from finding it and removing it. The field is overwritten in init().
        initialized = false;
    }

    public static synchronized void setLoggingEnabled(boolean enabled) {
        SpoilageConfig.getInstance().enable_logging = enabled;
        SpoilageConfig.getInstance().save();
        refreshConfigCache(); // Pass 94: keep the static flag cache in sync
        if (enabled) {
            init();
        } else {
            closeWriters();
        }
    }

    // Pass 94 (Lens 13): cached copies of the two config flags isTraceEnabled() reads. The
    // method is called at every guarded hot-path site (hopper transfers, inventory adds, GUI
    // clicks — see Pass 93), and each call did two SpoilageConfig.getInstance() volatile reads
    // plus two instance-field reads. Static volatile reads are the cheapest possible guard.
    // Refreshed by refreshConfigCache() on init, setLoggingEnabled, and config reload.
    private static volatile boolean cachedLoggingEnabled = false;
    private static volatile boolean cachedTraceEnabled = false;

    /** Re-read the config flags into the static cache. Call after any config load/reload. */
    public static void refreshConfigCache() {
        SpoilageConfig config = SpoilageConfig.getInstance();
        cachedLoggingEnabled = config.enable_logging;
        cachedTraceEnabled = config.enable_logging && config.enableTraceLogging;
    }

    public static boolean isTraceEnabled() { return initialized && cachedLoggingEnabled && cachedTraceEnabled; }

    public static void log(String message) {
        log(LogCategory.GENERAL, message);
    }

    public static void hud(String message) {
        log(LogCategory.HUD, message);
    }

    public static void log(LogCategory cat, Level world, String message) {
        if (!initialized || !SpoilageConfig.getInstance().enable_logging) {
            return;
        }
        if (world != null) {
            String dim = world.dimension().identifier().getPath();
            long tick = world.getGameTime();
            log(cat, "[Tick: " + tick + "] [Dim: " + dim + "] " + message);
        } else {
            log(cat, message);
        }
    }

    public static void log(LogCategory cat, String message) {
        if (!initialized || !SpoilageConfig.getInstance().enable_logging) {
            return;
        }
        if (cat == LogCategory.TRACE && !SpoilageConfig.getInstance().enableTraceLogging) {
            return;
        }

        // Non-blocking offer - if queue is full, drop the message to avoid TPS impact
        if (!logQueue.offer(new LogEntry(cat, message))) {
            droppedMessages++;
        }
    }

    public static void profile(LogCategory cat, String taskName, Runnable task) {
        if (!initialized || !SpoilageConfig.getInstance().enable_logging) {
            task.run();
            return;
        }
        long start = System.nanoTime();
        task.run();
        long elapsedNanos = System.nanoTime() - start;
        double ms = elapsedNanos / 1_000_000.0;
        if (ms > 0.5) {
            log(cat, "[Profiler] Task '" + taskName + "' executed in " + String.format("%.3f", ms) + " ms");
        }
    }
}