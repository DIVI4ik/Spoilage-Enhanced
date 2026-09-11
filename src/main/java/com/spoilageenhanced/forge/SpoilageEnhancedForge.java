package com.spoilageenhanced.forge;

import com.spoilageenhanced.SpoilageEnhancedCommon;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.platform.SpoilageEnhancedPlatform;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/**
 * Entrypoint for Forge (Minecraft 26.2+).
 */
@Mod("spoilage_enhanced")
public class SpoilageEnhancedForge {
    public static final String MOD_ID = "spoilage_enhanced";

    public SpoilageEnhancedForge() {
        SpoilageEnhancedPlatform.init(
                () -> getForgeConfigDir(),
                () -> getForgeGameDir()
        );
        SpoilageEnhancedLogger.init();
        ModDataComponentTypes.initialize();
        SpoilageEnhancedCommon.init();
        SpoilageEnhancedLogger.log("Spoilage Enhanced initialized via Forge (26.2 Universal Jar)!");
    }

    private static Path getForgeConfigDir() {
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirObj = fmlPathsClass.getField("CONFIGDIR").get(null);
            if (configDirObj != null) {
                return (Path) configDirObj.getClass().getMethod("get").invoke(configDirObj);
            }
        } catch (Throwable e) {
            // Pass 1117 (L1 — silent failure): the old catch swallowed every exception,
            // falling back to Path.of("config") with no signal. If FMLPaths is missing,
            // the field name changed, or the getter threw, the mod would silently write
            // its config to the wrong directory. Log at WARNING so a broken Forge install
            // is diagnosable instead of silently misbehaving.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "SpoilageEnhancedForge: failed to resolve Forge config dir via FMLPaths: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return Path.of("config");
    }

    private static Path getForgeGameDir() {
        try {
            Class<?> fmlPathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object gameDirObj = fmlPathsClass.getField("GAMEDIR").get(null);
            if (gameDirObj != null) {
                return (Path) gameDirObj.getClass().getMethod("get").invoke(gameDirObj);
            }
        } catch (Throwable e) {
            // Pass 1117 (L1 — silent failure): same as getForgeConfigDir — a silent
            // fallback to Path.of(".") hides a broken Forge environment. Log it.
            SpoilageEnhancedLogger.log(SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "SpoilageEnhancedForge: failed to resolve Forge game dir via FMLPaths: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return Path.of(".");
    }
}
