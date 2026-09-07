package com.spoilageenhanced.platform;

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
            } catch (Throwable ignored) {
            }
        }
        // Fallback for early access before onInitialize
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            if (loader != null) {
                return (Path) fabricLoaderClass.getMethod("getConfigDir").invoke(loader);
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirObj = fmlPathsClass.getField("CONFIGDIR").get(null);
            if (configDirObj != null) {
                return (Path) configDirObj.getClass().getMethod("get").invoke(configDirObj);
            }
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }
        return Path.of("config");
    }

    public static Path getGameDir() {
        if (gameDirSupplier != null) {
            try {
                return gameDirSupplier.get();
            } catch (Throwable ignored) {
            }
        }
        // Fallback for early access before onInitialize
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabricLoaderClass.getMethod("getInstance").invoke(null);
            if (loader != null) {
                return (Path) fabricLoaderClass.getMethod("getGameDir").invoke(loader);
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object gameDirObj = fmlPathsClass.getField("GAMEDIR").get(null);
            if (gameDirObj != null) {
                return (Path) gameDirObj.getClass().getMethod("get").invoke(gameDirObj);
            }
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }
        return Path.of(".");
    }
}
