package com.spoilageenhanced.neoforge;

import com.spoilageenhanced.SpoilageEnhancedCommon;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.neoforged.fml.common.Mod;

import java.nio.file.Path;

/**
 * Entrypoint for NeoForge (Minecraft 26.2+).
 */
@Mod("spoilage_enhanced")
public class SpoilageEnhancedNeoForge {
    public static final String MOD_ID = "spoilage_enhanced";

    public SpoilageEnhancedNeoForge() {
        SpoilageEnhancedPlatform.init(
                () -> getNeoForgeConfigDir(),
                () -> getNeoForgeGameDir()
        );
        SpoilageEnhancedLogger.init();
        ModDataComponentTypes.initialize();
        SpoilageEnhancedCommon.init();
        SpoilageEnhancedLogger.log("Spoilage Enhanced initialized via NeoForge (26.2 Universal Jar)!");
    }

    private static Path getNeoForgeConfigDir() {
        try {
            Class<?> fmlPathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object configDirObj = fmlPathsClass.getField("CONFIGDIR").get(null);
            if (configDirObj != null) {
                return (Path) configDirObj.getClass().getMethod("get").invoke(configDirObj);
            }
        } catch (Throwable e) {
            // Pass 1414 (L1 — silent failure): same fix as SpoilageEnhancedForge (pass 1117) —
            // the old catch swallowed every exception, falling back to Path.of("config") with
            // no signal. If FMLPaths is missing, the field name changed, or the getter threw,
            // the mod would silently write its config to the wrong directory. Log at WARNING so
            // a broken NeoForge install is diagnosable instead of silently misbehaving.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "SpoilageEnhancedNeoForge: failed to resolve NeoForge config dir via FMLPaths: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return Path.of("config");
    }

    private static Path getNeoForgeGameDir() {
        try {
            Class<?> fmlPathsClass = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object gameDirObj = fmlPathsClass.getField("GAMEDIR").get(null);
            if (gameDirObj != null) {
                return (Path) gameDirObj.getClass().getMethod("get").invoke(gameDirObj);
            }
        } catch (Throwable e) {
            // Pass 1414 (L1 — silent failure): same as getNeoForgeConfigDir — a silent
            // fallback to Path.of(".") hides a broken NeoForge environment. Log it.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "SpoilageEnhancedNeoForge: failed to resolve NeoForge game dir via FMLPaths: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return Path.of(".");
    }
}
