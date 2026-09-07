package com.spoilageenhanced;

import com.spoilageenhanced.util.AutoFoodDetector;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 255 regression test: AutoFoodDetector.detectAllFoodItems.
 *
 * <p>detectAllFoodItems iterates the item registry and returns a list of item IDs that
 * are spoilable. It is called during datapack reload to populate the config's
 * food_items list. This test verifies the basic contract: returns a non-null list,
 * contains known spoilable items, excludes never-spoilable items.</p>
 */
public class DetectAllFoodItemsTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void returnsNonNullList() {
        List<String> result = AutoFoodDetector.detectAllFoodItems();
        assertNotNull(result, "detectAllFoodItems must return a non-null list");
    }

    @Test
    void listIsNotEmpty() {
        List<String> result = AutoFoodDetector.detectAllFoodItems();
        assertFalse(result.isEmpty(), "detectAllFoodItems must find at least some food items");
    }

    @Test
    void listContainsNoDuplicates() {
        List<String> result = AutoFoodDetector.detectAllFoodItems();
        long distinct = result.stream().distinct().count();
        assertEquals(result.size(), distinct,
                "detectAllFoodItems must not return duplicate item IDs");
    }

    @Test
    void listContainsKnownSpoilableItems() {
        List<String> result = AutoFoodDetector.detectAllFoodItems();
        // EGG is always-spoilable (AutoFoodDetector.isAlwaysSpoilable).
        assertTrue(result.contains("minecraft:egg"),
                "detectAllFoodItems must include minecraft:egg (always-spoilable)");
    }

    @Test
    void listExcludesNeverSpoilableItems() {
        List<String> result = AutoFoodDetector.detectAllFoodItems();
        // STONE is never-spoilable (not a food item).
        assertFalse(result.contains("minecraft:stone"),
                "detectAllFoodItems must exclude minecraft:stone (never-spoilable)");
    }
}
