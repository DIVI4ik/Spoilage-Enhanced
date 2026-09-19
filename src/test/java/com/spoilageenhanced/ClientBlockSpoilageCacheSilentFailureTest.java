package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1370 (L1 — silent failure): test ClientBlockSpoilageCache's silent failure pattern.
 *
 * <p>ClientBlockSpoilageCache (ClientBlockSpoilageCache.java:190) catches {@code Throwable}
 * when sending a block spoilage request packet. It logs the failure at NETWORK category
 * but does not re-throw — the request simply fails silently from the player's perspective.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, logs at NETWORK category,
 * and uses the connection accessor pattern.</p>
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
    void requestBlockSpoilageHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around packet sending
        assertTrue(source.contains("try {"),
                "requestBlockSpoilage must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "requestBlockSpoilage must catch Throwable");
        assertTrue(source.contains("Failed to send block spoilage request"),
                "requestBlockSpoilage must log for failed send");
    }

    @Test
    void requestBlockSpoilageLogsAtNetworkCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs at NETWORK category
        assertTrue(source.contains("LogCategory.NETWORK"),
                "requestBlockSpoilage must log to NETWORK category");
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
        assertTrue(source.contains("connection.send(packet)"),
                "requestBlockSpoilage must send packet via connection");
    }

    @Test
    void requestBlockSpoilageCreatesCorrectPacket() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify it creates the correct packet
        assertTrue(source.contains("ServerboundCustomPayloadPacket"),
                "requestBlockSpoilage must create ServerboundCustomPayloadPacket");
        assertTrue(source.contains("BlockSpoilageRequestPayload(pendingPos)"),
                "requestBlockSpoilage must use BlockSpoilageRequestPayload with pendingPos");
    }

    @Test
    void requestBlockSpoilageLogsSuccess() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/client/ClientBlockSpoilageCache.java"))
                .replace("\r\n", "\n");

        // Verify it logs success
        assertTrue(source.contains("Requested block spoilage for"),
                "requestBlockSpoilage must log successful request");
    }
}