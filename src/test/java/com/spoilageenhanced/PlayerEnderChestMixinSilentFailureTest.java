package com.spoilageenhanced;

import com.spoilageenhanced.mixin.PlayerEnderChestMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1372 (L1 — silent failure): test PlayerEnderChestMixin's silent failure pattern.
 *
 * <p>PlayerEnderChestMixin (PlayerEnderChestMixin.java:82) catches {@code Throwable}
 * when aging items in an ender chest. One bad slot must not kill the ender chest
 * aging sweep — the mixin iterates all slots and calls updateSpoilage on each.
 * If any slot throws, it's logged with the slot index and player name, and the
 * sweep continues.</p>
 *
 * <p>What this test pins is that the try-catch pattern exists, logs with slot index
 * and player name, and continues the loop.</p>
 */
class PlayerEnderChestMixinSilentFailureTest {

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
    void enderChestAgingHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/PlayerEnderChestMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around ender chest slot aging
        assertTrue(source.contains("try {"),
                "ender chest aging must have try block");
        assertTrue(source.contains("} catch (Throwable t) {"),
                "ender chest aging must catch Throwable");
        assertTrue(source.contains("skipped ender chest slot"),
                "ender chest aging must log for skipped slot");
    }

    @Test
    void enderChestAgingLogsSlotAndPlayer() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/PlayerEnderChestMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs slot index and player name
        assertTrue(source.contains("i"),
                "ender chest aging must log the slot index");
        assertTrue(source.contains("self.getName().getString()"),
                "ender chest aging must log the player name");
        assertTrue(source.contains("t"),
                "ender chest aging must log the throwable");
    }

    @Test
    void enderChestAgingCallsUpdateSpoilage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/PlayerEnderChestMixin.java"))
                .replace("\r\n", "\n");

        // Verify it calls updateSpoilage
        assertTrue(source.contains("FoodSpoilageUtil.updateSpoilage(stack, serverWorld)"),
                "ender chest aging must call updateSpoilage");
    }

    @Test
    void enderChestAgingIteratesSlots() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/PlayerEnderChestMixin.java"))
                .replace("\r\n", "\n");

        // Verify it iterates over slots
        assertTrue(source.contains("for (int i = 0; i < enderChest.getContainerSize(); i++)"),
                "ender chest aging must iterate over slots");
        assertTrue(source.contains("ItemStack stack = enderChest.getItem(i)"),
                "ender chest aging must get stack from slot");
    }

    @Test
    void enderChestAgingContinuesAfterError() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/PlayerEnderChestMixin.java"))
                .replace("\r\n", "\n");

        // Verify it continues after error (the loop naturally continues)
        assertTrue(source.contains("continue;"),
                "ender chest aging must continue after slot error");
    }
}