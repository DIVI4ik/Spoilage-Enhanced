package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
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
 * Pass 1406 (L7 — boundary): overflow guard in CraftingSpoilageTransfer.
 *
 * <p>craftingSpoilageTransfer.compute() calculates expiration times as
 * {@code currentTime + Math.max(1L, baseRemaining + offset)}. When currentTime is
 * near Long.MAX_VALUE and baseRemaining is large, this addition overflows to negative,
 * making the crafted item appear already rotten on its first tick.</p>
 *
 * <p>The fix: clamp the sum to Long.MAX_VALUE when it would overflow, matching the
 * pattern used in FoodSpoilageUtil.initializeItemSpoilage (pass 1164) and
 * BlockSpoilageData (pass 1396).</p>
 */
public class CraftingSpoilageTransferOverflowTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    @Test
    void normalCraftingWorks() {
        // Basic sanity: a normal craft should work
        ItemStack wheat = new ItemStack(Items.WHEAT);
        wheat.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(10000L), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD);
        CraftingSpoilageTransfer.Result result = CraftingSpoilageTransfer.compute(
                List.of(wheat), bread, 0L);

        assertNotNull(result, "Normal craft should produce a result");
    }

    @Test
    void hugeCurrentTimeDoesNotOverflow() {
        // When currentTime is near Long.MAX_VALUE, the addition should not overflow
        long hugeTime = Long.MAX_VALUE - 1000L;
        long freshDuration = 10000L;

        ItemStack wheat = new ItemStack(Items.WHEAT);
        wheat.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(hugeTime + freshDuration), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD);
        CraftingSpoilageTransfer.Result result = CraftingSpoilageTransfer.compute(
                List.of(wheat), bread, hugeTime);

        // Should not crash and should produce a valid result
        assertNotNull(result, "Crafting with huge currentTime should not crash");
        
        // The expiration should be clamped to Long.MAX_VALUE, not overflow to negative
        SpoilageData data = result.data();
        assertFalse(data.freshExpirations().isEmpty(), "Should have fresh expirations");
        for (long exp : data.freshExpirations()) {
            assertTrue(exp > 0, "Expiration should be positive, got: " + exp);
            assertTrue(exp <= Long.MAX_VALUE, "Expiration should not overflow, got: " + exp);
        }
    }

    @Test
    void maxCurrentTimeDoesNotOverflow() {
        // Edge case: currentTime = Long.MAX_VALUE
        long maxTime = Long.MAX_VALUE;
        long freshDuration = 10000L;

        ItemStack wheat = new ItemStack(Items.WHEAT);
        wheat.set(ModDataComponentTypes.SPOILAGE,
                new SpoilageData(List.of(maxTime + freshDuration), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD);
        CraftingSpoilageTransfer.Result result = CraftingSpoilageTransfer.compute(
                List.of(wheat), bread, maxTime);

        // Should not crash
        assertNotNull(result, "Crafting with max currentTime should not crash");
    }
}
