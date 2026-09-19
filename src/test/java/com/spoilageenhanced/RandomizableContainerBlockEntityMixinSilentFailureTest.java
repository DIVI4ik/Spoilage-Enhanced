package com.spoilageenhanced;

import com.spoilageenhanced.mixin.RandomizableContainerBlockEntityMixin;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1373 (L1 — silent failure): test RandomizableContainerBlockEntityMixin's silent failure pattern.
 *
 * <p>RandomizableContainerBlockEntityMixin (RandomizableContainerBlockEntityMixin.java:49)
 * catches {@code Exception} when randomizing spoilage for container contents. If any
 * slot throws during randomization, it's logged and the finally block ensures the
 * randomizing flag is reset.</p>
 *
 * <p>What this test pins is that the try-catch-finally pattern exists, logs the
 * exception, and resets the randomizing flag in finally.</p>
 */
class RandomizableContainerBlockEntityMixinSilentFailureTest {

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
    void randomizeHasTryCatch() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/RandomizableContainerBlockEntityMixin.java"))
                .replace("\r\n", "\n");

        // Verify the try-catch pattern exists around randomization
        assertTrue(source.contains("try {"),
                "randomize must have try block");
        assertTrue(source.contains("} catch (Exception e) {"),
                "randomize must catch Exception");
        assertTrue(source.contains("Exception during randomize"),
                "randomize must log for exception");
    }

    @Test
    void randomizeHasFinally() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/RandomizableContainerBlockEntityMixin.java"))
                .replace("\r\n", "\n");

        // Verify it has finally block
        assertTrue(source.contains("finally {"),
                "randomize must have finally block");
        assertTrue(source.contains("RandomizableContainerHelper.IS_RANDOMIZING.set(Boolean.FALSE)"),
                "randomize must reset randomizing flag in finally");
    }

    @Test
    void randomizeChecksSpoilageData() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/RandomizableContainerBlockEntityMixin.java"))
                .replace("\r\n", "\n");

        // Verify it checks spoilage data
        assertTrue(source.contains("stack.get(ModDataComponentTypes.SPOILAGE)"),
                "randomize must check spoilage data");
        assertTrue(source.contains("data == null || data.isEmpty()"),
                "randomize must check for null or empty data");
    }

    @Test
    void randomizeCallsRandomizeSpoilage() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/RandomizableContainerBlockEntityMixin.java"))
                .replace("\r\n", "\n");

        // Verify it calls randomizeSpoilage
        assertTrue(source.contains("FoodSpoilageUtil.randomizeSpoilage(stack, world, rand)"),
                "randomize must call randomizeSpoilage");
        assertTrue(source.contains("this.setItem(i, stack)"),
                "randomize must set the item back");
    }

    @Test
    void randomizeLogsSlotAndItem() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/mixin/RandomizableContainerBlockEntityMixin.java"))
                .replace("\r\n", "\n");

        // Verify it logs slot and item
        assertTrue(source.contains("Slot \" + i + \": randomized"),
                "randomize must log slot and item");
    }
}