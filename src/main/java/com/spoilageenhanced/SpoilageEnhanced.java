package com.spoilageenhanced;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpoilageEnhanced implements ModInitializer {
	public static final String MOD_ID = "spoilage_enhanced";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		com.spoilageenhanced.platform.SpoilageEnhancedPlatform.init(
				() -> net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(),
				() -> net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
		com.spoilageenhanced.SpoilageEnhancedCommon.init();

		LOGGER.info("SpoilageEnhanced initialized!");
	}
}