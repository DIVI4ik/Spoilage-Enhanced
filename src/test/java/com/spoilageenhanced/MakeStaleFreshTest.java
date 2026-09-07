package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
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
 * Pass 230 regression test: FoodSpoilageUtil.makeStale and makeFresh.
 *
 * <p>These methods set the entire stack to STALE or FRESH with a uniform expiration.
 * They are called from the command handlers (givespoiled, etc.) and from the
 * furnace purify-on-smelt path. The pre-world guard clauses (null/empty/non-spoilable)
 * can be tested without a Level.</p>
 */
public class MakeStaleFreshTest {

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
    void makeStaleEmptyStackIsNoOp() {
        ItemStack stack = ItemStack.EMPTY;
        // Must not throw — pre-world guard handles empty.
        FoodSpoilageUtil.makeStale(stack, null);
        assertTrue(stack.isEmpty(), "Empty stack remains empty");
    }

    @Test
    void makeStaleNonSpoilableIsNoOp() {
        ItemStack stack = new ItemStack(Items.STONE);
        FoodSpoilageUtil.makeStale(stack, null);
        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable stack must not get a SPOILAGE component");
    }

    @Test
    void makeFreshEmptyStackIsNoOp() {
        ItemStack stack = ItemStack.EMPTY;
        FoodSpoilageUtil.makeFresh(stack, null);
        assertTrue(stack.isEmpty(), "Empty stack remains empty");
    }

    @Test
    void makeFreshNonSpoilableIsNoOp() {
        ItemStack stack = new ItemStack(Items.STONE);
        FoodSpoilageUtil.makeFresh(stack, null);
        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable stack must not get a SPOILAGE component");
    }
}
