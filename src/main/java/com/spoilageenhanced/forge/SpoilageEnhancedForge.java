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
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }
        return Path.of(".");
    }
}
