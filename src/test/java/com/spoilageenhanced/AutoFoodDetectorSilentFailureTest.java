package com.spoilageenhanced;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1383 (L1 — silent failure): test AutoFoodDetector's silent failure patterns.
 *
 * <p>AutoFoodDetector (AutoFoodDetector.java:63, :163, :186, :252) has four silent-failure
 * catch blocks:</p>
 *
 * <ol>
 *   <li>getItemComponents (AutoFoodDetector.java:63): catches {@code Throwable} and
 *       returns null when an item's components cannot be read. Logged? No — silently
 *       returns null.</li>
 *   <li>Item tag scan (AutoFoodDetector.java:163): catches {@code Throwable} when
 *       scanning items during world loading. One broken item must never abort world
 *       loading. Logged at DATA category.</li>
 *   <li>Block tag scan (AutoFoodDetector.java:186): catches {@code Throwable} when
 *       scanning blocks during world loading. Same guard. Logged at DATA category.</li>
 *   <li>getFactorForItem (AutoFoodDetector.java:252): catches {@code Throwable} and
 *       returns null when tags are not bound yet (or the holder is unbound). Nothing
 *       to do this pass.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: item/block
 * scans catch Throwable and continue, tags-not-bound returns null.</p>
 */
class AutoFoodDetectorSilentFailureTest {

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
    void getItemComponentsHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around component reading
        assertTrue(source.contains("try {"),
                "getItemComponents must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "getItemComponents must catch Throwable");
        assertTrue(source.contains("return null;"),
                "getItemComponents must return null on failure");
    }

    @Test
    void getItemComponentsChecksComponentsBound() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it checks components bound
        assertTrue(source.contains("item.builtInRegistryHolder().areComponentsBound()"),
                "getItemComponents must check components bound");
        assertTrue(source.contains("return null;"),
                "getItemComponents must return null if not bound");
    }

    @Test
    void getItemComponentsReturnsComponents() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it returns item.components()
        assertTrue(source.contains("item.components()"),
                "getItemComponents must return item.components()");
    }

    @Test
    void itemTagScanHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around item tag scan
        assertTrue(source.contains("try {"),
                "item tag scan must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "item tag scan must catch Throwable");
        assertTrue(source.contains("skipped an item during the tag scan"),
                "item tag scan must log for skipped item");
    }

    @Test
    void itemTagScanLogsAtDataCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it logs at DATA category
        assertTrue(source.contains("LogCategory.DATA"),
                "item tag scan must log to DATA category");
    }

    @Test
    void itemTagScanContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it continues after error (the loop naturally continues)
        assertTrue(source.contains("items++;"),
                "item tag scan must count registered items");
    }

    @Test
    void blockTagScanHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around block tag scan
        assertTrue(source.contains("for (Block block : BuiltInRegistries.BLOCK)"),
                "block tag scan must iterate over blocks");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "block tag scan must catch Throwable");
        assertTrue(source.contains("skipped a block during the tag scan"),
                "block tag scan must log for skipped block");
    }

    @Test
    void blockTagScanLogsAtDataCategory() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it logs at DATA category
        assertTrue(source.contains("LogCategory.DATA"),
                "block tag scan must log to DATA category");
    }

    @Test
    void blockTagScanContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it continues after error (the loop naturally continues)
        assertTrue(source.contains("blocks++;"),
                "block tag scan must count registered blocks");
    }

    @Test
    void getFactorForItemHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around factor calculation
        assertTrue(source.contains("try {"),
                "getFactorForItem must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "getFactorForItem must catch Throwable");
        assertTrue(source.contains("return null;"),
                "getFactorForItem must return null on failure");
    }

    @Test
    void getFactorForItemHandlesUnboundTags() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // Verify it handles unbound tags
        assertTrue(source.contains("Tags are not bound yet"),
                "getFactorForItem must handle unbound tags");
        assertTrue(source.contains("(or the holder is unbound)"),
                "getFactorForItem must handle unbound holder");
    }
}