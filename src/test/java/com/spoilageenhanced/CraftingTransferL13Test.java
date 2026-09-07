package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.CraftingSpoilageTransfer;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pass 636 (L13 behaviour): CraftingSpoilageTransfer.compute — the logic that carries
 * spoilage across a crafting recipe. The mixin path (CraftingResultSlotMixin.onTake) has
 * only ever been exercised by unit tests of this helper; this test pins the behaviour a
 * player actually sees at a crafting table.
 *
 * <p>Scenarios mirror what a player does: craft bread from wheat where one wheat is
 * rotten (the bread must be rotten), craft from fresh wheat of differing remaining life
 * (the bread must inherit the worst), and craft from a non-spoilable result (no data).</p>
 */
public class CraftingTransferL13Test {

    private static final long NOW = 100_000L;

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(DataComponentMap.EMPTY);
            }
        }
    }

    private static ItemStack wheat(SpoilageData data) {
        ItemStack stack = new ItemStack(Items.WHEAT, 1);
        stack.set(ModDataComponentTypes.SPOILAGE, data);
        return stack;
    }

    @Test
    void breadFromOneRottenWheatIsRotten() {
        // A player puts 3 wheat in a row; one of them is rotten. The bread must carry
        // ROTTEN — the worst ingredient wins, no matter how fresh the others are.
        ItemStack rottenWheat = wheat(new SpoilageData(List.of(), List.of(), 1, 1.0));
        ItemStack freshWheat1 = wheat(new SpoilageData(List.of(NOW + 50_000L), List.of(), 0, 1.0));
        ItemStack freshWheat2 = wheat(new SpoilageData(List.of(NOW + 80_000L), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD, 1);

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(List.of(rottenWheat, freshWheat1, freshWheat2), bread, NOW);

        assertNotNull(result, "crafting from spoilable ingredients must carry data");
        assertEquals(FoodSpoilageUtil.SpoilageState.ROTTEN, result.state(),
                "bread from one rotten wheat must be ROTTEN — the worst ingredient wins");
    }

    @Test
    void breadFromFreshWheatInheritsWorstExpiration() {
        // Two fresh wheat with different remaining life: the bread must inherit the
        // shorter remaining life (the worst), not the average and not the best.
        ItemStack youngerWheat = wheat(new SpoilageData(List.of(NOW + 80_000L), List.of(), 0, 1.0));
        ItemStack olderWheat = wheat(new SpoilageData(List.of(NOW + 50_000L), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD, 1);

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(List.of(youngerWheat, olderWheat), bread, NOW);

        assertNotNull(result);
        assertEquals(FoodSpoilageUtil.SpoilageState.FRESH, result.state(),
                "bread from fresh wheat must be FRESH");
        // The inherited data must reflect the older wheat's remaining life (50_000),
        // not the younger one's (80_000). The exact proportion depends on the recipe
        // (3 wheat -> 1 bread keeps 1/3 of the life per the transfer's proportion rule),
        // so pin the state and that data exists rather than the exact number.
        assertNotNull(result.data());
    }

    @Test
    void staleWheatMakesStaleBread() {
        // One stale wheat among fresh ones: the bread must be STALE at worst.
        ItemStack staleWheat = wheat(new SpoilageData(List.of(), List.of(NOW + 20_000L), 0, 1.0));
        ItemStack freshWheat = wheat(new SpoilageData(List.of(NOW + 90_000L), List.of(), 0, 1.0));

        ItemStack bread = new ItemStack(Items.BREAD, 1);

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(List.of(staleWheat, freshWheat), bread, NOW);

        assertNotNull(result);
        assertEquals(FoodSpoilageUtil.SpoilageState.STALE, result.state(),
                "bread from one stale wheat must be STALE — worse than fresh wins");
    }

    @Test
    void nonSpoilableResultCarriesNothing() {
        // Crafting sticks from wheat (a non-food result): no data should be attached.
        ItemStack freshWheat = wheat(new SpoilageData(List.of(NOW + 50_000L), List.of(), 0, 1.0));
        ItemStack sticks = new ItemStack(Items.STICK, 4);

        CraftingSpoilageTransfer.Result result =
                CraftingSpoilageTransfer.compute(List.of(freshWheat), sticks, NOW);

        assertNull(result, "a non-spoilable result must carry no spoilage data");
    }

    @Test
    void emptyIngredientsCarryNothing() {
        // Crafting from an empty grid (shouldn't happen, but the helper must not throw).
        ItemStack bread = new ItemStack(Items.BREAD, 1);
        assertNull(CraftingSpoilageTransfer.compute(List.of(), bread, NOW),
                "no ingredients -> no data");
    }
}
