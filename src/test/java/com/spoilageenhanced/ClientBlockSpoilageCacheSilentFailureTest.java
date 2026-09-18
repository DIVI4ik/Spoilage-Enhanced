package com.spoilageenhanced;

import com.spoilageenhanced.client.ClientBlockSpoilageCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1353 (L1 — silent failure): test ClientBlockSpoilageCache's silent failure pattern.
 *
 * <p>ClientBlockSpoilageCache.requestBlockSpoilage (ClientBlockSpoilageCache.java:190)
 * catches {@code Throwable} when sending a block spoilage request packet to the server.
 * This is a network operation that can fail for many reasons (connection closed,
 * serialization error, etc.). The catch logs the failure and continues — the HUD
 * will show the "checking" placeholder until a response arrives or times out.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists and logs appropriately.</p>
 */
class ClientBlockSpoilageCacheSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : net.minecraft.core.registries.BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<?> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void requestBlockSpoilageSourceHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around packet sending
        assertTrue(source.contains("try {"),
                "requestBlockSpoilage must have try block for packet sending");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "requestBlockSpoilage must catch Throwable for packet sending");
        assertTrue(source.contains("Failed to send block spoilage request"),
                "requestBlockSpoilage must log for failed packet send");
    }

    @Test
    void requestBlockSpoilageUsesConnectionAccessor() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify it uses the connection accessor pattern
        assertTrue(source.contains("ClientCommonPacketListenerImplAccessor"),
                "requestBlockSpoilage must use ClientCommonPacketListenerImplAccessor");
        assertTrue(source.contains("spoilage_enhanced$getConnection()"),
                "requestBlockSpoilage must use accessor method");
    }

    @Test
    void requestBlockSpoilageLogsNetworkCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs to NETWORK category
        assertTrue(source.contains("LogCategory.NETWORK"),
                "requestBlockSpoilage must log to NETWORK category");
    }

    @Test
    void requestBlockSpoilageHasFallbackSend() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify fallback to connection.send()
        assertTrue(source.contains("connection.send(packet)"),
                "requestBlockSpoilage must have fallback send");
    }
}