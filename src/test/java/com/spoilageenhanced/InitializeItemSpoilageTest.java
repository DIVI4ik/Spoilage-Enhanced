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
 * Pass 276 regression test: FoodSpoilageUtil.initializeItemSpoilage.
 *
 * <p>initializeItemSpoilage sets the SPOILAGE component on a stack if it doesn't have
 * one yet. It is called when a stack is first created (crafting, pickup, etc.). The
 * pre-world guard clauses (null/empty/non-spoilable) can be tested without a Level.</p>
 */
public class InitializeItemSpoilageTest {

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
    void emptyStackIsNoOp() {
        ItemStack stack = ItemStack.EMPTY;
        FoodSpoilageUtil.initializeItemSpoilage(stack, null);
        assertTrue(stack.isEmpty(), "Empty stack remains empty");
    }

    @Test
    void nonSpoilableStackIsNoOp() {
        ItemStack stack = new ItemStack(Items.STONE);
        FoodSpoilageUtil.initializeItemSpoilage(stack, null);
        assertFalse(stack.has(ModDataComponentTypes.SPOILAGE),
                "Non-spoilable stack must not get a SPOILAGE component");
    }

    @Test
    void emptySpoilageDataIsRepaired() {
        // Pass 503 (L1 — silent failure): an empty SPOILAGE component (DEFAULT data)
        // must be repaired by initializeItemSpoilage, not left empty forever.
        ItemStack stack = new ItemStack(Items.CARROT, 1);
        stack.set(ModDataComponentTypes.SPOILAGE, SpoilageData.DEFAULT);
        assertTrue(stack.has(ModDataComponentTypes.SPOILAGE),
                "Stack has SPOILAGE component (empty)");
        assertTrue(stack.get(ModDataComponentTypes.SPOILAGE).isEmpty(),
                "Component is empty (DEFAULT)");

        FoodSpoilageUtil.initializeItemSpoilage(stack, null);

        SpoilageData data = stack.get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(data, "SPOILAGE component must be present after repair");
        assertFalse(data.isEmpty(), "Empty data must be repaired to fresh data");
        assertEquals(1, data.freshExpirations().size(),
                "Fresh data must have 1 expiration for count=1");
    }
}
