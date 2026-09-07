package com.spoilageenhanced.fabric;

import com.spoilageenhanced.SpoilageEnhancedCommon;
import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.SpoilageEnhancedLogger;
import net.fabricmc.api.ModInitializer;

public class SpoilageEnhancedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SpoilageEnhancedLogger.init();
        ModDataComponentTypes.initialize();
        SpoilageEnhancedCommon.init();

        SpoilageEnhancedLogger.log("Spoilage Enhanced initialized via Fabric Loader (26.2 Universal Jar)!");
    }
}
