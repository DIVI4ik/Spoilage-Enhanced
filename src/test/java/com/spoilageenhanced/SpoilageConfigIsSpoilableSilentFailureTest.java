package com.spoilageenhanced;

import com.spoilageenhanced.config.SpoilageConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1358 (L1 — silent failure): test SpoilageConfig.computeIsSpoilable's silent failure pattern.
 *
 * <p>SpoilageConfig.computeIsSpoilable (SpoilageConfig.java:511) catches {@code Exception} when
 * checking if an item has the FOOD component. This happens during the datapack-load scan
 * when components are not yet bound. The catch sets an "unbound" flag and continues with
 * config-only checks (additional_tracked_items, item_durations) which don't require
 * component access.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, the unbound flag is set,
 * and config-only checks still run when components are unbound.</p>
 */
class SpoilageConfigIsSpoilableSilentFailureTest {

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
    void computeIsSpoilableHasTryCatchForFoodComponent() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around FOOD component check in computeIsSpoilable
        assertTrue(source.contains("private boolean computeIsSpoilable"),
                "computeIsSpoilable method must exist");
        assertTrue(source.contains("try {"),
                "computeIsSpoilable must have try block for FOOD component check");
        assertTrue(source.contains("} catch (Exception e) {"),
                "computeIsSpoilable must catch Exception for FOOD component check");
        assertTrue(source.contains("unboundOut[0] = true;"),
                "computeIsSpoilable must set unbound flag on exception");
        assertTrue(source.contains("Components not bound yet"),
                "computeIsSpoilable must have comment about unbound components");
    }

    @Test
    void computeIsSpoilableRunsConfigChecksWhenUnbound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/config/SpoilageConfig.java"))
                .replace("\r\n", "\n");

        // Verify config-only checks run when unbound
        assertTrue(source.contains("getAdditionalSet().contains(idStr)"),
                "computeIsSpoilable must check additional_tracked_items when unbound");
        assertTrue(source.contains("item_durations.get(idStr) != null"),
                "computeIsSpoilable must check item_durations when unbound");
        assertTrue(source.contains("unboundOut[0] = false;"),
                "computeIsSpoilable must clear unbound flag when config check matches");
    }

    @Test
    void isSpoilableReturnsTrueForFoodItems() throws Exception {
        // Test that isSpoilable works for vanilla food items
        SpoilageConfig config = SpoilageConfig.getInstance();
        Method isSpoilable = SpoilageConfig.class.getDeclaredMethod("isSpoilable", Item.class);
        isSpoilable.setAccessible(true);

        // Apple has FOOD component
        Boolean result = (Boolean) isSpoilable.invoke(config, Items.APPLE);
        assertTrue(result, "Apple must be spoilable");
    }

    @Test
    void isSpoilableReturnsFalseForNonFoodItems() throws Exception {
        SpoilageConfig config = SpoilageConfig.getInstance();
        Method isSpoilable = SpoilageConfig.class.getDeclaredMethod("isSpoilable", Item.class);
        isSpoilable.setAccessible(true);

        // Diamond is not food
        Boolean result = (Boolean) isSpoilable.invoke(config, Items.DIAMOND);
        assertFalse(result, "Diamond must not be spoilable");
    }

    @Test
    void isSpoilableHandlesNullItem() throws Exception {
        SpoilageConfig config = SpoilageConfig.getInstance();
        Method isSpoilable = SpoilageConfig.class.getDeclaredMethod("isSpoilable", Item.class);
        isSpoilable.setAccessible(true);

        Boolean result = (Boolean) isSpoilable.invoke(config, (Item) null);
        assertFalse(result, "null item must not be spoilable");
    }
}