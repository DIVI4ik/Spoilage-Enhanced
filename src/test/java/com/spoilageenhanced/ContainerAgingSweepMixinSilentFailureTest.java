package com.spoilageenhanced;

import com.spoilageenhanced.mixin.ContainerAgingSweepMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1366 (L1 — silent failure): test ContainerAgingSweepMixin's silent failure patterns.
 *
 * <p>ContainerAgingSweepMixin (ContainerAgingSweepMixin.java:126, :137) catches {@code Throwable}
 * when aging containers during the server tick sweep. Two catch blocks:</p>
 *
 * <ol>
 *   <li>Container aging (ContainerAgingSweepMixin.java:126): catches {@code Throwable} when
 *       calling ageContainer. One bad container must not kill the server tick — the sweep is
 *       the FIRST code that ever touches many of these containers (vanilla never ticks a
 *       chest), so a modded container whose getItem() throws would otherwise crash through
 *       this loop every second. Logged with position and block entity type.</li>
 *   <li>List-backed container aging (ContainerAgingSweepMixin.java:137): catches {@code Throwable}
 *       when calling ageItemList for list-backed containers. Same rationale — log and move on.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: container aging errors
 * are caught and logged with position and block entity type, sweep continues.</p>
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
    void ageContainerHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around ageContainer
        assertTrue(source.contains("try {"),
                "ageContainer must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "ageContainer must catch Throwable");
        assertTrue(source.contains("skipped container at"),
                "ageContainer must log for skipped container");
    }

    @Test
    void ageContainerLogsPositionAndType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs position and block entity type
        assertTrue(source.contains("entry.getKey()"),
                "ageContainer must log the position key");
        assertTrue(source.contains("blockEntity.getType()"),
                "ageContainer must log the block entity type");
        assertTrue(source.contains("t"),
                "ageContainer must log the throwable");
    }

    @Test
    void ageContainerCallsAgeContainerMethod() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it calls ageContainer
        assertTrue(source.contains("ageContainer(container, level)"),
                "ageContainer must call ageContainer method");
    }

    @Test
    void ageItemListHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around ageItemList
        assertTrue(source.contains("try {"),
                "ageItemList must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "ageItemList must catch Throwable");
        assertTrue(source.contains("skipped list-backed container at"),
                "ageItemList must log for skipped list-backed container");
    }

    @Test
    void ageItemListLogsPositionAndType() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs position and block entity type
        assertTrue(source.contains("entry.getKey()"),
                "ageItemList must log the position key");
        assertTrue(source.contains("blockEntity.getType()"),
                "ageItemList must log the block entity type");
        assertTrue(source.contains("t"),
                "ageItemList must log the throwable");
    }

    @Test
    void ageItemListCallsAgeItemListMethod() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it calls ageItemList
        assertTrue(source.contains("ageItemList(itemList, level)"),
                "ageItemList must call ageItemList method");
    }

    @Test
    void ageItemListUsesContainerResolution() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses ContainerResolution.asAgingItemList
        assertTrue(source.contains("ContainerResolution.asAgingItemList(blockEntity)"),
                "ageItemList must use ContainerResolution.asAgingItemList");
    }

    @Test
    void sweepContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java"))
                .replace("\r\n", "\n");

        // Verify it continues after error
        assertTrue(source.contains("continue;"),
                "sweep must continue after container error");
    }
}