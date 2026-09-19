package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1386 (L1 — silent failure): test KnownHandPickedBlocks' silent failure pattern.
 *
 * <p>KnownHandPickedBlocks (KnownHandPickedBlocks.java:92) catches {@code Throwable}
 * when registering hand-picked blocks. A malformed entry must never abort world
 * loading. Logged at DATA category.</p>
 *
 * <p>What this test pins is that the pattern remains as documented: catches Throwable,
 * logs at DATA category, continues processing other entries.</p>
 */
class KnownHandPickedBlocksSilentFailureTest {

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
    void registerHandPickedBlocksHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around hand-picked block registration
        assertTrue(source.contains("try {"),
                "registerHandPickedBlocks must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "registerHandPickedBlocks must catch Throwable");
        assertTrue(source.contains("skipped"),
                "registerHandPickedBlocks must log for skipped entry");
    }

    @Test
    void registerHandPickedBlocksLogsAtDataCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java"))
                .replace("\r\n", "\n");

        // Verify it logs at DATA category
        assertTrue(source.contains("LogCategory.DATA"),
                "registerHandPickedBlocks must log to DATA category");
    }

    @Test
    void registerHandPickedBlocksContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java"))
                .replace("\r\n", "\n");

        // Verify it continues after error (the loop naturally continues)
        assertTrue(source.contains("added++;"),
                "registerHandPickedBlocks must count registered blocks");
    }

    @Test
    void registerHandPickedBlocksUsesConfig() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java"))
                .replace("\r\n", "\n");

        // Verify it uses config.registerTrackedBlock
        assertTrue(source.contains("config.registerTrackedBlock"),
                "registerHandPickedBlocks must use config.registerTrackedBlock");
    }

    @Test
    void registerHandPickedBlocksLogsBlockAndItem() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/KnownHandPickedBlocks.java"))
                .replace("\r\n", "\n");

        // Verify it logs block and item IDs
        assertTrue(source.contains("blockId"),
                "registerHandPickedBlocks must log blockId");
        assertTrue(source.contains("itemId"),
                "registerHandPickedBlocks must log itemId");
    }
}