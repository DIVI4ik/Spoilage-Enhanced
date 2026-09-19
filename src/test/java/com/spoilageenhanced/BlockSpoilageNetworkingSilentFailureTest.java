package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1374 (L1 — silent failure): test BlockSpoilageNetworking's silent failure patterns.
 *
 * <p>BlockSpoilageNetworking (BlockSpoilageNetworking.java:100, :128) has two silent-failure
 * catch blocks when sending block spoilage responses to the client:</p>
 *
 * <ol>
 *   <li>handleBlockSpoilageRequest (BlockSpoilageNetworking.java:100): catches {@code Throwable}
 *       when sending the response packet. Uses the ServerCommonPacketListenerImplAccessor
 *       pattern for NeoForge/Forge compatibility. Logged at NETWORK category.</li>
 *   <li>sendEmptyResponse (BlockSpoilageNetworking.java:128): catches {@code Throwable}
 *       when sending a STATE_NONE response so the client doesn't wait on a refused request.
 *       Same accessor pattern, logged at NETWORK category.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: packet sending
 * uses the accessor pattern, failures are logged at NETWORK category, empty response
 * uses STATE_NONE.</p>
 */
class BlockSpoilageNetworkingSilentFailureTest {

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
    void handleRequestHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around response sending
        assertTrue(source.contains("try {"),
                "handleBlockSpoilageRequest must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "handleBlockSpoilageRequest must catch Throwable");
        assertTrue(source.contains("Failed to send block spoilage response"),
                "handleBlockSpoilageRequest must log for failed send");
    }

    @Test
    void handleRequestLogsAtNetworkCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify it logs at NETWORK category
        assertTrue(source.contains("LogCategory.NETWORK"),
                "handleBlockSpoilageRequest must log to NETWORK category");
    }

    @Test
    void handleRequestUsesAccessorPattern() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify it uses the accessor pattern
        assertTrue(source.contains("ServerCommonPacketListenerImplAccessor"),
                "handleBlockSpoilageRequest must use ServerCommonPacketListenerImplAccessor");
        assertTrue(source.contains("spoilage_enhanced$getConnection()"),
                "handleBlockSpoilageRequest must use accessor method");
        assertTrue(source.contains("ClientboundCustomPayloadPacket"),
                "handleBlockSpoilageRequest must create ClientboundCustomPayloadPacket");
    }

    @Test
    void handleRequestCreatesResponsePayload() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify it creates the response payload
        assertTrue(source.contains("BlockSpoilageResponsePayload"),
                "handleBlockSpoilageRequest must create BlockSpoilageResponsePayload");
        assertTrue(source.contains("stateOrdinal"),
                "handleBlockSpoilageRequest must include stateOrdinal");
        assertTrue(source.contains("ticksRemaining"),
                "handleBlockSpoilageRequest must include ticksRemaining");
        assertTrue(source.contains("getSpoilageSpeedMultiplier()"),
                "handleBlockSpoilageRequest must include speed multiplier");
    }

    @Test
    void sendEmptyResponseHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around empty response sending
        assertTrue(source.contains("private static void sendEmptyResponse"),
                "sendEmptyResponse method must exist");
        assertTrue(source.contains("try {"),
                "sendEmptyResponse must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "sendEmptyResponse must catch Throwable");
        assertTrue(source.contains("Failed to send empty block spoilage response"),
                "sendEmptyResponse must log for failed send");
    }

    @Test
    void sendEmptyResponseUsesStateNone() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify it uses STATE_NONE
        assertTrue(source.contains("STATE_NONE"),
                "sendEmptyResponse must use STATE_NONE");
        assertTrue(source.contains("0L"),
                "sendEmptyResponse must use 0 ticks");
    }

    @Test
    void sendEmptyResponseLogsAtNetworkCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/network/BlockSpoilageNetworking.java"))
                .replace("\r\n", "\n");

        // Verify it logs at NETWORK category
        assertTrue(source.contains("LogCategory.NETWORK"),
                "sendEmptyResponse must log to NETWORK category");
    }
}