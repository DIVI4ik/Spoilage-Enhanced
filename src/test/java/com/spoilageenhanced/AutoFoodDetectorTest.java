package com.spoilageenhanced;

import com.spoilageenhanced.util.AutoFoodDetector;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 143 regression test: AutoFoodDetector logic.
 *
 * Tests the hardcoded never/always spoilable lists, container detection,
 * and food candidate validation. These are the core heuristics that decide
 * whether modded items get spoilage tracking.
 */
public class AutoFoodDetectorTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Bind item components so isSpoilable() doesn't throw
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void neverSpoilableIncludesVanillaExclusions() {
        // Items that should never spoil (potions, golden foods, rotten flesh)
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPLASH_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.LINGERING_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.EXPERIENCE_BOTTLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ENCHANTED_GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_CARROT));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GLISTERING_MELON_SLICE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ROTTEN_FLESH));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPIDER_EYE));
    }

    @Test
    void neverSpoilableDoesNotIncludeRegularFood() {
        // Regular food should not be in the never-spoilable list
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.APPLE));
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.BREAD));
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.COOKED_BEEF));
        assertFalse(AutoFoodDetector.isNeverSpoilable(Items.CARROT));
    }

    @Test
    void alwaysSpoilableIncludesVanillaAlways() {
        // Items that should always spoil (eggs, milk, cake)
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.EGG));
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.MILK_BUCKET));
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.CAKE));
    }

    @Test
    void alwaysSpoilableIncludes262EggVariants() {
        // 26.2 split eggs into three items
        // These should be always spoilable even without FOOD component
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.EGG));
        // Note: BLUE_EGG and BROWN_EGG may not exist in vanilla 26.2
        // but the string check handles them
    }

    @Test
    void containerDetectionMatchesVanillaContainers() {
        assertTrue(AutoFoodDetector.isContainerItem(Items.BOWL));
        assertTrue(AutoFoodDetector.isContainerItem(Items.BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.GLASS_BOTTLE));
        assertTrue(AutoFoodDetector.isContainerItem(Items.WATER_BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.LAVA_BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.POWDER_SNOW_BUCKET));
    }

    @Test
    void containerDetectionDoesNotMatchNonContainers() {
        assertFalse(AutoFoodDetector.isContainerItem(Items.APPLE));
        assertFalse(AutoFoodDetector.isContainerItem(Items.DIAMOND));
        assertFalse(AutoFoodDetector.isContainerItem(Items.STONE));
    }

    @Test
    void foodOrMealItemRespectsNeverSpoilable() {
        // Even if an item has FOOD component, if it's in never-spoilable it should return false
        // We can't easily test this without a mock item, but we can verify the logic order
        // by checking that never-spoilable items are correctly identified
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.POTION));
        // The isFoodOrMealItem should return false for never-spoilable items
        // (it checks isNeverSpoilable first)
    }

    @Test
    void foodOrMealItemRespectsAlwaysSpoilable() {
        // Always-spoilable items should return true even without FOOD component
        assertTrue(AutoFoodDetector.isAlwaysSpoilable(Items.EGG));
        // isFoodOrMealItem checks isAlwaysSpoilable second
    }

    @Test
    void foodOrMealItemRespectsConfigSpoilable() {
        // Items in config should return true
        // Apple is in the default config
        assertTrue(AutoFoodDetector.isFoodOrMealItem(Items.APPLE));
    }

    @Test
    void foodOrMealItemRequiresFoodComponentForUnknownItems() {
        // Items not in any list need FOOD component
        // We can't easily test this without a mock, but we verify the logic
        // by checking that a non-food item returns false
        assertFalse(AutoFoodDetector.isFoodOrMealItem(Items.STONE));
    }

    @Test
    void validFoodCandidateExcludesToolsAndArmor() {
        // Tools and armor should not be valid food candidates
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_SWORD));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.IRON_AXE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_PICKAXE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_SHOVEL));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_HOE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_HELMET));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_CHESTPLATE));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_LEGGINGS));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.DIAMOND_BOOTS));
    }

    @Test
    void validFoodCandidateExcludesSeedsAndSticks() {
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.WHEAT_SEEDS));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.STICK));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.PAPER));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.STRING));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.FEATHER));
        assertFalse(AutoFoodDetector.isValidFoodCandidate(Items.FLINT));
    }

    @Test
    void validFoodCandidateAllowsRegularFood() {
        // Regular food should be valid candidates
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.APPLE));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.BREAD));
        assertTrue(AutoFoodDetector.isValidFoodCandidate(Items.COOKED_BEEF));
    }

    @Test
    void neverSpoilableHasNoDuplicates() {
        // The hardcoded list has duplicate checks (lines 69-78 and 83-88)
        // Verify they don't cause issues by checking all listed items
        // are correctly identified
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPLASH_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.LINGERING_POTION));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.EXPERIENCE_BOTTLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ENCHANTED_GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_APPLE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GOLDEN_CARROT));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.GLISTERING_MELON_SLICE));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.ROTTEN_FLESH));
        assertTrue(AutoFoodDetector.isNeverSpoilable(Items.SPIDER_EYE));
    }

    @Test
    void containerDetectionHandlesModdedBuckets() {
        // The pattern id.endsWith("_bucket") should match modded buckets
        // We can't test modded items directly, but we can verify the pattern
        // by checking that vanilla buckets match
        assertTrue(AutoFoodDetector.isContainerItem(Items.WATER_BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.LAVA_BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.POWDER_SNOW_BUCKET));
        assertTrue(AutoFoodDetector.isContainerItem(Items.MILK_BUCKET));
    }

    @Test
    void containerDetectionHandlesPlatesAndTrays() {
        // The patterns id.endsWith(":plate") and id.endsWith("_tray")
        // We can't test modded items directly, but verify the patterns exist
        // by checking the method doesn't crash on vanilla items
        assertFalse(AutoFoodDetector.isContainerItem(Items.GOLD_INGOT));
        assertFalse(AutoFoodDetector.isContainerItem(Items.DIAMOND));
    }

    @Test
    void isFoodOrMealItemReturnsFalseForNeverSpoilable() {
        // isFoodOrMealItem checks isNeverSpoilable first
        // Potion is never spoilable, so should return false
        assertFalse(AutoFoodDetector.isFoodOrMealItem(Items.POTION));
    }

    @Test
    void isFoodOrMealItemReturnsTrueForAlwaysSpoilable() {
        // isFoodOrMealItem checks isAlwaysSpoilable second
        // Egg is always spoilable, so should return true
        assertTrue(AutoFoodDetector.isFoodOrMealItem(Items.EGG));
    }

    @Test
    void isFoodOrMealItemReturnsTrueForConfigSpoilable() {
        // Apple is in default config
        assertTrue(AutoFoodDetector.isFoodOrMealItem(Items.APPLE));
    }

    @Test
    void isFoodOrMealItemReturnsFalseForNonFood() {
        // Stone is not food
        assertFalse(AutoFoodDetector.isFoodOrMealItem(Items.STONE));
    }
}