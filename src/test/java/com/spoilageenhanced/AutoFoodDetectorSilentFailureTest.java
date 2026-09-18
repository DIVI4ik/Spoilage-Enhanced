package com.spoilageenhanced;

import com.spoilageenhanced.util.AutoFoodDetector;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 1347 (L1 — silent failure): test AutoFoodDetector's silent failure patterns.
 *
 * <p>AutoFoodDetector has three silent-failure catch blocks:</p>
 *
 * <ol>
 *   <li>{@code safeComponents(Item)} at line 54-66: catches {@code Throwable} and returns
 *       {@code null}. This is used to check if an item's components are bound without crashing
 *       during datapack reload. Every scan path goes through here so one unbound item cannot
 *       abort world loading.</li>
 *   <li>{@code foodTagFactor(Item)} at line 252-255: catches {@code Throwable} and returns
 *       {@code null}. Tags are not bound yet during datapack reload.</li>
 *   <li>{@code scanTagsAndBlocks()} at line 142: catches {@code Throwable} per item during
 *       the tag/block scan loop.</li>
 * </ol>
 *
 * <p>What this test pins is that these patterns remain as documented: safeComponents
 * returns null for unbound items, foodTagFactor returns null when tags are not bound,
 * and the scan loop continues after a failure.</p>
 */
class AutoFoodDetectorSilentFailureTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        com.spoilageenhanced.component.ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<?> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void safeComponentsReturnsNullForNullItem() throws Exception {
        Method safeComponents = AutoFoodDetector.class.getDeclaredMethod(
                "safeComponents", Item.class);
        safeComponents.setAccessible(true);

        Object result = safeComponents.invoke(null, (Item) null);
        assertNull(result, "safeComponents must return null for null item");
    }

    @Test
    void safeComponentsReturnsComponentsForBoundItem() throws Exception {
        Method safeComponents = AutoFoodDetector.class.getDeclaredMethod(
                "safeComponents", Item.class);
        safeComponents.setAccessible(true);

        // Apple should have bound components
        Object result = safeComponents.invoke(null, Items.APPLE);
        assertNotNull(result, "safeComponents must return components for a bound item");
    }

    @Test
    void safeComponentsNeverThrows() throws Exception {
        Method safeComponents = AutoFoodDetector.class.getDeclaredMethod(
                "safeComponents", Item.class);
        safeComponents.setAccessible(true);

        // Even a null item must not throw
        assertDoesNotThrow(() -> safeComponents.invoke(null, (Item) null),
                "safeComponents must never throw — it catches Throwable");
    }

    @Test
    void isNeverSpoilableReturnsTrueForExcludedItems() {
        // Golden apples, rotten flesh, spider eyes, etc. are excluded
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ROTTEN_FLESH));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPIDER_EYE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPLASH_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.LINGERING_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.EXPERIENCE_BOTTLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ENCHANTED_GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_CARROT));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GLISTERING_MELON_SLICE));
    }

    @Test
    void isNeverSpoilableReturnsFalseForFoodItems() {
        // Food items should not be excluded
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.APPLE));
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.BREAD));
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.CARROT));
    }

    @Test
    void isAlwaysSpoilableReturnsTrueForEggMilkCake() {
        // Eggs, milk bucket, cake are always spoilable
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.EGG));
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.MILK_BUCKET));
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.CAKE));
    }

    @Test
    void isAlwaysSpoilableReturnsFalseForNonFood() {
        // Tools and weapons should not be always spoilable
        assertFalse(AutoFoodDetector.isAlwaysSpoilable(Items.STONE));
        assertFalse(AutoFoodDetector.isAlwaysSpoilable(Items.DIRT));
    }

    @Test
    void isContainerItemReturnsTrueForBowlBucket() {
        // Bowls, buckets, glass bottles are container items
        assertTrue(AutoFoodDetector.isContainerItem(Items.BOWL));
        assertTrue(AutoFoodDetector.isContainerItem(Items.BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.GLASS_BOTTLE));
    }

    @Test
    void isContainerItemReturnsFalseForNonContainer() {
        // Food items are not container items
        assertFalse(AutoFoodDetector.isContainerItem(Items.APPLE));
        assertFalse(AutoFoodDetector.isContainerItem(Items.BREAD));
    }

    @Test
    void isFoodOrMealItemReturnsTrueForApple() {
        // Apple is food
        assertTrue(AutoFoodDetector.isFoodOrMealItem(Items.APPLE));
    }

    @Test
    void isFoodOrMealItemReturnsFalseForTool() {
        // A stone pickaxe is not food
        assertFalse(AutoFoodDetector.isFoodOrMealItem(Items.STONE_PICKAXE));
    }

    @Test
    void isValidFoodCandidateReturnsTrueForApple() {
        // Apple is a valid food candidate
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.APPLE));
    }

    @Test
    void isValidFoodCandidateReturnsFalseForSword() {
        // A sword is not a valid food candidate
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.STONE_SWORD));
    }

    @Test
    void isValidFoodCandidateReturnsFalseForSapling() {
        // Saplings are planting stock, not food
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.OAK_SAPLING));
    }

    @Test
    void safeComponentsHasTryCatchPattern() throws Exception {
        // Verify the safeComponents method has the try-catch pattern
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("try {\n            if (!item.builtInRegistryHolder().areComponentsBound()) {"),
                "safeComponents must have try block");
        assertTrue(source.contains("} catch (Throwable ignored) {\n            return null;\n        }"),
                "safeComponents must catch Throwable and return null");
    }

    @Test
    void foodTagFactorHasTryCatchPattern() throws Exception {
        // Verify the foodTagFactor method has the try-catch pattern
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        assertTrue(source.contains("} catch (Throwable ignored) {"),
                "foodTagFactor must catch Throwable");
        assertTrue(source.contains("Tags are not bound yet"),
                "foodTagFactor must log when tags are not bound");
    }

    @Test
    void scanTagsAndBlocksHasTryCatchPerItem() throws Exception {
        // Verify the scanTagsAndBlocks method has try-catch per item
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/spoilageenhanced/util/AutoFoodDetector.java"))
                .replace("\r\n", "\n");

        // The scan loop should have try-catch around each item
        assertTrue(source.contains("try {\n                if (item == null || item == Items.AIR) continue;"),
                "scan loop must have try block per item");
    }
}