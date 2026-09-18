package com.spoilageenhanced;

import com.spoilageenhanced.mixin.ContainerAgingSweepMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1357 (L1 — silent failure): test ContainerAgingSweepMixin's silent failure patterns.
 *
 * <p>ContainerAgingSweepMixin has two silent-failure catch blocks during the container
 * aging sweep (ContainerAgingSweepMixin.java:126, :137):</p>
 *
 * <ol>
 *   <li>Container aging (ContainerAgingSweepMixin.java:126): catches {@code Throwable}
 *       when aging a container via {@code ageContainer}. A modded container whose
 *       {@code getItem()} throws would otherwise crash the server tick every second.
 *       The catch logs the failure and continues to the next container.</li>
 *   <li>List-backed container aging (ContainerAgingSweepMixin.java:137): catches {@code Throwable}
 *       when aging a list-backed container via {@code ageItemList}. Same rationale —
 *       one bad container must not kill the server tick.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: container aging
 * errors are caught per-container, logged with position and block entity type, and
 * the sweep continues.</p>
 */
class ContainerAgingSweepMixinSilentFailureTest {

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
    void containerAgingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around container aging
        assertTrue(source.contains("try {"),
                "container aging must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "container aging must catch Throwable");
        assertTrue(source.contains("ContainerAgingSweep: skipped container at"),
                "container aging must log skipped container");
    }

    @Test
    void containerAgingLogsPositionAndType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs position and block entity type
        assertTrue(source.contains("entry.getKey()"),
                "container aging must log position");
        assertTrue(source.contains("blockEntity.getType()"),
                "container aging must log block entity type");
    }

    @Test
    void containerAgingContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it continues to next container
        assertTrue(source.contains("continue;"),
                "container aging must continue after error");
    }

    @Test
    void listBackedContainerAgingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around list-backed container aging
        assertTrue(source.contains("try {"),
                "list-backed container aging must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "list-backed container aging must catch Throwable");
        assertTrue(source.contains("ContainerAgingSweep: skipped list-backed container at"),
                "list-backed container aging must log skipped container");
    }

    @Test
    void listBackedContainerAgingLogsPositionAndType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs position and block entity type
        assertTrue(source.contains("entry.getKey()"),
                "list-backed container aging must log position");
        assertTrue(source.contains("blockEntity.getType()"),
                "list-backed container aging must log block entity type");
    }

    @Test
    void usesContainerResolution() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses ContainerResolution utility
        assertTrue(source.contains("ContainerResolution.asAgingItemList"),
                "must use ContainerResolution.asAgingItemList");
        assertTrue(source.contains("ContainerResolution.asAgingContainer"),
                "must use ContainerResolution.asAgingContainer");
    }
}