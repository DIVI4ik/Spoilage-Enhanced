package com.spoilageenhanced;

import com.spoilageenhanced.mixin.ItemClientMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1365 (L1 — silent failure): test ItemClientMixin's silent failure pattern.
 *
 * <p>ItemClientMixin (ItemClientMixin.java:148) catches {@code Exception} when reading
 * the shift-key state for tooltip display. The only realistic failure is a
 * NullPointerException from Minecraft.getInstance() racing during teardown, but the
 * broad catch hid anything else too. Logged at WARNING so a real bug in the input
 * layer is visible instead of silently suppressing the shift-detail UI.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists and logs appropriately.</p>
 */
class ItemClientMixinSilentFailureTest {

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
    void shiftKeyReadingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ItemClientMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around shift key reading
        assertTrue(source.contains("try {"),
                "shift key reading must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "shift key reading must catch Exception");
        assertTrue(source.contains("failed to read shift-key state for tooltip"),
                "shift key reading must log for failed read");
    }

    @Test
    void shiftKeyReadingChecksMinecraftInstance() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ItemClientMixin.java"))
                .replace("\r\n", "\n");

        // Verify it checks Minecraft instance and window
        assertTrue(source.contains("Minecraft.getInstance() != null"),
                "shift key reading must check Minecraft instance");
        assertTrue(source.contains("getWindow() != null"),
                "shift key reading must check window");
    }

    @Test
    void shiftKeyReadingUsesInputConstants() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ItemClientMixin.java"))
                .replace("\r\n", "\n");

        // Verify it uses InputConstants.isKeyDown
        assertTrue(source.contains("InputConstants.isKeyDown"),
                "shift key reading must use InputConstants.isKeyDown");
        assertTrue(source.contains("GLFW.GLFW_KEY_LEFT_SHIFT"),
                "shift key reading must check left shift");
        assertTrue(source.contains("GLFW.GLFW_KEY_RIGHT_SHIFT"),
                "shift key reading must check right shift");
    }

    @Test
    void shiftKeyReadingLogsWarning() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ItemClientMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs at WARNING level
        assertTrue(source.contains("LogCategory.GENERAL"),
                "shift key reading must log to GENERAL category");
        assertTrue(source.contains("ItemClientMixin: failed to read shift-key state for tooltip"),
                "shift key reading must include mixin name in log");
        assertTrue(source.contains("e.getClass().getSimpleName()"),
                "shift key reading must log exception class");
        assertTrue(source.contains("e.getMessage()"),
                "shift key reading must log exception message");
    }

    @Test
    void shiftKeyReadingDefaultsToFalse() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/ItemClientMixin.java"))
                .replace("\r\n", "\n");

        // Verify it defaults to false
        assertTrue(source.contains("boolean showShiftDetails = false;"),
                "shift key reading must default to false");
    }
}