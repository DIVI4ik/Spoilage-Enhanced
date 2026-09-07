package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 277 regression test: FoodSpoilageUtil.updateSpoilage.
 *
 * <p>updateSpoilage is the main entry point for advancing spoilage state. It is called
 * from the inventory tick, the item entity tick, and the crafting/hopper transfer paths.
 * The pre-world guard clauses (null/empty/non-spoilable) can be tested without a Level.</p>
 */
public class UpdateSpoilageTest {

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
    void nullStackIsNoOp() {
        // Must not throw — pre-world guard handles null.
        FoodSpoilageUtil.updateSpoilage(null, null);
    }

    @Test
    void emptyStackIsNoOp() {
        ItemStack stack = ItemStack.EMPTY;
        FoodSpoilageUtil.updateSpoilage(stack, null);
        assertTrue(stack.isEmpty(), "Empty stack remains empty");
    }

    @Test
    void nullWorldIsNoOp() {
        ItemStack stack = new ItemStack(Items.EGG);
        FoodSpoilageUtil.updateSpoilage(stack, null);
        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Null world must not initialize spoilage");
    }

    @Test
    void nonSpoilableStackIsNoOp() {
        ItemStack stack = new ItemStack(Items.STONE);
        FoodSpoilageUtil.updateSpoilage(stack, null);
        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable stack must not get a SPOILAGE component");
    }
}
