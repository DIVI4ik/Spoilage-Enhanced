package com.spoilageenhanced;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common mod initialization shared between Fabric and Forge.
 * Platform-specific entrypoints call init() after setting up SpoilageEnhancedPlatform.
 */
public class SpoilageEnhancedCommon {
    public static final String MOD_ID = "spoilage_enhanced";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        // Force initialization of payload packet classes so their static initializers run
        // and the payload codec registration mixins (ClientboundPayloadTypesMixin,
        // ServerboundPayloadTypesMixin) can apply. These classes are normally only loaded
        // when a player connects, so without this the mixins silently fail (require = 0).
        try {
            Class.forName(ServerboundCustomPayloadPacket.class.getName(), true, ServerboundCustomPayloadPacket.class.getClassLoader());
            Class.forName(ClientboundCustomPayloadPacket.class.getName(), true, ClientboundCustomPayloadPacket.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Should never happen - these are vanilla classes
        }

        com.spoilageenhanced.util.SpoilageEnhancedLogger.init();
        LOGGER.info("SpoilageEnhanced common initialization complete!");
    }
}
