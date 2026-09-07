package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 407 (L13 observed): crafter rotten ingredient behavior — what a player sees.
 *
 * <p>Scenario: player puts 1 rotten apple + 2 fresh apples in a crafter, crafts 3 apples.
 * CrafterBlockMixin intercepts dispenseItem and calls CraftingSpoilageTransfer.compute:
 * - worst ingredient state is ROTTEN -> result gets ROTTEN state
 * - worst ingredient state is STALE -> result gets STALE state
 * - worst ingredient state is FRESH -> result gets FRESH state
 *
 * The mixin needs ServerLevel for level.getGameTime(). This test pins the
 * CraftingSpoilageTransfer.compute logic which is the core of the behavior.
 *
 * What should happen: rotten ingredient makes the result rotten.
 */
public class CrafterRottenLogicTest {

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
    void rottenIngredientMakesResultRotten() {
        // Scenario: 1 rotten apple + 2 fresh apples in crafter
        List<ItemStack> ingredients = new ArrayList<>();
        ItemStack rotten = new ItemStack(Items.APPLE, 1);
        rotten.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(), List.of(), 1, 1.0));
        ingredients.add(rotten);

        ItemStack fresh1 = new ItemStack(Items.APPLE, 1);
        fresh1.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0));
        ingredients.add(fresh1);

        ItemStack fresh2 = new ItemStack(Items.APPLE, 1);
        fresh2.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0));
        ingredients.add(fresh2);

        ItemStack result = new ItemStack(Items.APPLE, 3);

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNotNull(transfer, "compute must return non-null for spoilable ingredients");
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, transfer.state(),
                "Result state must be ROTTEN when any ingredient is rotten");
        assertEquals(3, transfer.data().rottenCount(),
                "Result must have 3 rotten items (worst-first transfer)");
    }

    @Test
    void staleIngredientMakesResultStale() {
        // Scenario: 1 stale apple + 2 fresh apples in crafter
        List<ItemStack> ingredients = new ArrayList<>();
        ItemStack stale = new ItemStack(Items.APPLE, 1);
        stale.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0));
        ingredients.add(stale);

        ItemStack fresh1 = new ItemStack(Items.APPLE, 1);
        fresh1.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0));
        ingredients.add(fresh1);

        ItemStack fresh2 = new ItemStack(Items.APPLE, 1);
        fresh2.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0));
        ingredients.add(fresh2);

        ItemStack result = new ItemStack(Items.APPLE, 3);

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNotNull(transfer);
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, transfer.state(),
                "Result state must be STALE when worst ingredient is stale");
        assertEquals(3, transfer.data().staleExpirations().size(),
                "Result must have 3 stale items");
    }

    @Test
    void freshIngredientsMakeResultFresh() {
        // Scenario: 3 fresh apples in crafter
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ItemStack fresh = new ItemStack(Items.APPLE, 1);
            fresh.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0));
            ingredients.add(fresh);
        }

        ItemStack result = new ItemStack(Items.APPLE, 3);

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNotNull(transfer);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, transfer.state(),
                "Result state must be FRESH when all ingredients are fresh");
        assertEquals(3, transfer.data().freshExpirations().size(),
                "Result must have 3 fresh items");
    }

    @Test
    void nonSpoilableResultInheritsNothing() {
        // Scenario: crafting a non-spoilable item (e.g., stone) from spoilable ingredients
        List<ItemStack> ingredients = new ArrayList<>();
        ItemStack rotten = new ItemStack(Items.APPLE, 1);
        rotten.set(ModDataComponentTypes.SPOILAGE, new SpoilageData(List.of(), List.of(), 1, 1.0));
        ingredients.add(rotten);

        ItemStack result = new ItemStack(Items.STONE, 1);

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNull(transfer, "compute must return null for non-spoilable result");
    }

    @Test
    void emptyIngredientsReturnsNull() {
        // Scenario: empty ingredient list
        List<ItemStack> ingredients = new ArrayList<>();
        ItemStack result = new ItemStack(Items.APPLE, 1);

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNull(transfer, "compute must return null for empty ingredients");
    }

    @Test
    void emptyResultReturnsNull() {
        // Scenario: empty result stack
        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(new ItemStack(Items.APPLE, 1));
        ItemStack result = ItemStack.EMPTY;

        CraftingSpoilageTransfer.Result transfer = CraftingSpoilageTransfer.compute(ingredients, result, System.currentTimeMillis());
        assertNull(transfer, "compute must return null for empty result");
    }
}