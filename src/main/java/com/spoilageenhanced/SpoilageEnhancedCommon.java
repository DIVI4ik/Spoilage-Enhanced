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
        } catch (ClassNotFoundException e) {
            // Pass 1120 (L1 — silent failure): the old catch swallowed the exception
            // with 'Should never happen - these are vanilla classes'. If it DOES happen
            // (mod conflict, broken install, classloader isolation), the payload codec
            // mixins silently fail to register, and network packets break with no signal.
            // Log at ERROR so a broken install is diagnosable.
            com.spoilageenhanced.util.SpoilageEnhancedLogger.log(
                    com.spoilageenhanced.util.SpoilageEnhancedLogger.LogCategory.GENERAL,
                    "SpoilageEnhancedCommon.init: vanilla payload packet class not found — "
                    + "network codecs will not register: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }

        com.spoilageenhanced.util.SpoilageEnhancedLogger.init();
        LOGGER.info("SpoilageEnhanced common initialization complete!");
    }
}
