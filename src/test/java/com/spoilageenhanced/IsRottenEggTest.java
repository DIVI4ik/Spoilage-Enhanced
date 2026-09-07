package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 169 regression test: isRottenEgg correctness.
 *
 * AnimalEntityMixin uses isRottenEgg to decide whether to spawn a chicken from
 * a rotten egg (or skip the rotten-egg hatch path). A wrong answer would either
 * make every rotten egg hatch into a chicken (wrong) or never spawn one (also
 * wrong). None of the existing tests covered this path.
 */
public class IsRottenEggTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void emptyStackIsNotRottenEgg() {
        assertFalse(FoodSpoilageUtil.isRottenEgg(ItemStack.EMPTY, null));
    }

    @Test
    void nonEggItemIsNotRottenEgg() {
        ItemStack apple = new ItemStack(Items.APPLE, 1);
        apple.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(), 1, 1.0));
        assertFalse(FoodSpoilageUtil.isRottenEgg(apple, null),
                "An apple with all-rotten data must not be mistaken for a rotten egg");
    }

    @Test
    void freshEggIsNotRottenEgg() {
        // A fresh egg has no component, or a component with all fresh trackers.
        ItemStack egg = new ItemStack(Items.EGG, 1);
        egg.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(100_000L), List.of(), 0, 1.0));
        assertFalse(FoodSpoilageUtil.isRottenEgg(egg, null),
                "A fresh egg must not be rotten");
    }

    @Test
    void entirelyRottenEggIsRottenEgg() {
        // count 1, rottenCount 1, no fresh/stale: isEntirelyRotten → true
        ItemStack egg = new ItemStack(Items.EGG, 1);
        egg.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(), 1, 1.0));
        assertTrue(FoodSpoilageUtil.isRottenEgg(egg, null),
                "An entirely-rotten egg must be detected as rotten");
    }

    @Test
    void partiallyRottenEggIsNotEntirelyRotten() {
        // count 2, rottenCount 1, fresh 1: isEntirelyRotten requires rottenCount >= count
        ItemStack egg = new ItemStack(Items.EGG, 2);
        egg.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(100_000L), List.of(), 1, 1.0));
        assertFalse(FoodSpoilageUtil.isRottenEgg(egg, null),
                "A partially-rotten egg is not entirely rotten");
    }

    @Test
    void staleEggIsNotRottenEgg() {
        // All stale, no rotten: isEntirelyRotten → false
        ItemStack egg = new ItemStack(Items.EGG, 1);
        egg.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(), List.of(50_000L), 0, 1.0));
        assertFalse(FoodSpoilageUtil.isRottenEgg(egg, null),
                "A stale egg is not rotten");
    }
}