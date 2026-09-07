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
 * Pass 411 (L13 observed): furnace smelting behavior — what a player sees.
 *
 * <p>Scenario: player smelts rotten beef in a furnace. AbstractFurnaceBlockEntityMixin:
 * - canBurn: if the input stack is ENTIRELY rotten, block smelting (return false)
 * - burn: the output gets a fresh timer added (smelting purifies)
 *
 * The mixin needs ServerLevel and AbstractFurnaceBlockEntity. This test pins the
 * isEntirelyRotten logic that gates smelting.
 *
 * What should happen: entirely-rotten input blocks smelting; partially-rotten does not.
 */
public class FurnaceSmeltLogicTest {

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
    void entirelyRottenBeefBlocksSmelting() {
        // Scenario: player puts 1 entirely-rotten beef in furnace
        // What should happen: canBurn returns false (smelting blocked)
        ItemStack stack = new ItemStack(Items.BEEF, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertTrue(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Entirely-rotten beef must block smelting");
    }

    @Test
    void partiallyRottenBeefDoesNotBlockSmelting() {
        // Scenario: player puts 2 beef (1 rotten, 1 fresh) in furnace
        // What should happen: canBurn does NOT return false (smelting proceeds)
        ItemStack stack = new ItemStack(Items.BEEF, 2);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 1, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertFalse(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Partially-rotten beef (1 rotten + 1 fresh) must NOT block smelting");
    }

    @Test
    void staleBeefDoesNotBlockSmelting() {
        // Scenario: player puts 1 stale beef in furnace
        // What should happen: canBurn does NOT return false (smelting proceeds)
        ItemStack stack = new ItemStack(Items.BEEF, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(System.currentTimeMillis() + 10000L), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertFalse(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Stale beef must NOT block smelting");
    }

    @Test
    void freshBeefDoesNotBlockSmelting() {
        // Scenario: player puts 1 fresh beef in furnace
        // What should happen: canBurn does NOT return false (smelting proceeds)
        ItemStack stack = new ItemStack(Items.BEEF, 1);
        SpoilageData data = new SpoilageData(List.of(System.currentTimeMillis() + 10000L), List.of(), 0, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertFalse(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Fresh beef must NOT block smelting");
    }

    @Test
    void componentlessBeefDoesNotBlockSmelting() {
        // Scenario: player puts 1 fresh beef (no component yet) in furnace
        // What should happen: canBurn does NOT return false (smelting proceeds)
        ItemStack stack = new ItemStack(Items.BEEF, 1);
        // No component set

        assertFalse(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Component-less beef must NOT block smelting");
    }

    @Test
    void rottenCountExceedingStackCountBlocksSmelting() {
        // Scenario: over-tracked stack (count=1, rottenCount=2 from copyWithCount)
        // What should happen: isEntirelyRotten returns true (rottenCount >= count)
        ItemStack stack = new ItemStack(Items.BEEF, 1);
        SpoilageData data = new SpoilageData(List.of(), List.of(), 2, 1.0);
        stack.set(ModDataComponentTypes.SPOILAGE, data);

        assertTrue(FoodSpoilageUtil.isEntirelyRotten(stack),
                "Over-tracked rotten stack (rottenCount=2 >= count=1) must block smelting");
    }
}