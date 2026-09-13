package com.spoilageenhanced.platform;

import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Simple platform abstraction for paths that differ between Fabric and Forge.
 * Initialized at startup by the platform-specific entrypoint, with resilient fallbacks.
 */
public class SpoilageEnhancedPlatform {
    private static Supplier<Path> configDirSupplier;
    private static Supplier<Path> gameDirSupplier;

    public static void init(Supplier<Path> configDir, Supplier<Path> gameDir) {
        SpoilageEnhancedPlatform.configDirSupplier = configDir;
        SpoilageEnhancedPlatform.gameDirSupplier = gameDir;
    }

    public static Path getConfigDir() {
        if (configDirSupplier != null) {
            try {
                return configDirSupplier.get();
            } catch (Throwable t) {
                // Pass 1174 (L1 — silent failure): the old catch swallowed the supplier's
                // failure and fell through to the reflection probes below with no signal.
                // A supplier that throws means the platform entrypoint is broken; the
                // probes may still succeed, but the failure should be visible either way.
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: config dir supplier threw "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + " — falling back to loader reflection");
            }
        }
        // Fallback for early access before onInitialize
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            if (loader != null) {
                return (Path) fabricLoaderClass.getMethod("getConfigDir").invoke(loader);
            }
        } catch (Throwable t) {
            // ClassNotFound is the NORMAL case on Forge/NeoForge — the Fabric loader is
            // simply not there. Only a non-missing-class failure means a broken Fabric
            // install, and that is worth a line.
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: FabricLoader probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirObj = fmlPathsClass.getField("CONFIGDIR").get(null);
            if (configDirObj != null) {
                return (Path) configDirObj.getClass().getMethod("get").invoke(configDirObj);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: Forge FMLPaths probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        // NeoForge renamed the FML package: the Forge class above does not exist there, so the
        // config dir silently fell back to the relative "config" path. NeoForge's own class is
        // net.neoforged.fml.loading.FMLPaths with the same CONFIGDIR/GAMEDIR enum shape.
        try {
            Class<?> fmlPathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object configDirObj = fmlPathsClass.getField("CONFIGDIR").get(null);
            if (configDirObj != null) {
                return (Path) configDirObj.getClass().getMethod("get").invoke(configDirObj);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: NeoForge FMLPaths probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        // Pass 1174 (L1 — silent failure): reaching this line means every probe failed.
        // The old code returned silently; a mod writing its config to a wrong relative
        // directory is exactly the failure a player cannot diagnose without this line.
        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                "SpoilageEnhancedPlatform: no loader could resolve the config dir"
                + " — falling back to relative 'config'. Config writes may land in the"
                + " wrong directory on this install.");
        return Path.of("config");
    }

    public static Path getGameDir() {
        if (gameDirSupplier != null) {
            try {
                return gameDirSupplier.get();
            } catch (Throwable t) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: game dir supplier threw "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + " — falling back to loader reflection");
            }
        }
        // Fallback for early access before onInitialize
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            if (loader != null) {
                return (Path) fabricLoaderClass.getMethod("getGameDir").invoke(loader);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: FabricLoader probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object gameDirObj = fmlPathsClass.getField("GAMEDIR").get(null);
            if (gameDirObj != null) {
                return (Path) gameDirObj.getClass().getMethod("get").invoke(gameDirObj);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: Forge FMLPaths probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        // NeoForge renamed the FML package: the Forge class above does not exist there, so the
        // game dir silently fell back to the relative "." path. NeoForge's own class is
        // net.neoforged.fml.loading.FMLPaths with the same CONFIGDIR/GAMEDIR enum shape.
        try {
            Class<?> fmlPathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object gameDirObj = fmlPathsClass.getField("GAMEDIR").get(null);
            if (gameDirObj != null) {
                return (Path) gameDirObj.getClass().getMethod("get").invoke(gameDirObj);
            }
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                        "SpoilageEnhancedPlatform: NeoForge FMLPaths probe failed unexpectedly: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                "SpoilageEnhancedPlatform: no loader could resolve the game dir"
                + " — falling back to relative '.'. Config writes may land in the"
                + " wrong directory on this install.");
        return Path.of(".");
    }
}
