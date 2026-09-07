package com.spoilageenhanced;

import com.spoilageenhanced.component.ModDataComponentTypes;
import com.spoilageenhanced.component.SpoilageData;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import com.spoilageenhanced.util.FoodSpoilageUtil.SpoilageState;
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
 * Pass 162 regression test: getWorstTimestamp / getBestTimestamp correctness.
 *
 * These methods feed GourdBlockMixin — a placed block inherits the timestamp of the
 * worst/best item in the stack. A wrong min/max would launder a spoiled stack into a
 * fresh block (BUG-12). They were only exercised by HotPathBenchmarkTest for speed,
 * never for correctness.
 */
public class TimestampExtractionTest {

    @BeforeAll
    static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ModDataComponentTypes.initialize();
        for (var ref : BuiltInRegistries.ITEM.asHolderIdMap()) {
            if (!ref.areComponentsBound() && ref instanceof net.minecraft.core.Holder.Reference<Item> reference) {
                reference.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            }
        }
    }

    private static ItemStack stack(SpoilageData data) {
        ItemStack stack = new ItemStack(Items.APPLE, 1);
        if (data != null) stack.set(ModDataComponentTypes.SPOILAGE, data);
        return stack;
    }

    @Test
    void worstTimestampPicksMinimumFresh() {
        // 3 fresh items with different expirations — worst = the earliest (minimum)
        SpoilageData data = new SpoilageData(List.of(5000L, 1000L, 3000L), List.of(), 0, 1.0);
        assertEquals(1000L, FoodSpoilageUtil.getWorstTimestamp(stack(data), SpoilageState.FRESH),
                "Worst fresh timestamp must be the MINIMUM expiration");
    }

    @Test
    void bestTimestampPicksMaximumFresh() {
        SpoilageData data = new SpoilageData(List.of(5000L, 1000L, 3000L), List.of(), 0, 1.0);
        assertEquals(5000L, FoodSpoilageUtil.getBestTimestamp(stack(data), SpoilageState.FRESH),
                "Best fresh timestamp must be the MAXIMUM expiration");
    }

    @Test
    void worstTimestampPicksMinimumStale() {
        SpoilageData data = new SpoilageData(List.of(), List.of(7000L, 2000L, 4000L), 0, 1.0);
        assertEquals(2000L, FoodSpoilageUtil.getWorstTimestamp(stack(data), SpoilageState.STALE),
                "Worst stale timestamp must be the MINIMUM expiration");
    }

    @Test
    void bestTimestampPicksMaximumStale() {
        SpoilageData data = new SpoilageData(List.of(), List.of(7000L, 2000L, 4000L), 0, 1.0);
        assertEquals(7000L, FoodSpoilageUtil.getBestTimestamp(stack(data), SpoilageState.STALE),
                "Best stale timestamp must be the MAXIMUM expiration");
    }

    @Test
    void nullDataReturnsMinusOne() {
        assertEquals(-1L, FoodSpoilageUtil.getWorstTimestamp(stack(null), SpoilageState.FRESH));
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack(null), SpoilageState.FRESH));
    }

    @Test
    void emptyDataReturnsMinusOne() {
        SpoilageData empty = SpoilageData.DEFAULT;
        assertEquals(-1L, FoodSpoilageUtil.getWorstTimestamp(stack(empty), SpoilageState.FRESH));
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack(empty), SpoilageState.FRESH));
    }

    @Test
    void stateWithEmptyListReturnsMinusOne() {
        // Asking for the fresh timestamp when only stale items exist
        SpoilageData data = new SpoilageData(List.of(), List.of(2000L), 0, 1.0);
        assertEquals(-1L, FoodSpoilageUtil.getWorstTimestamp(stack(data), SpoilageState.FRESH),
                "Fresh timestamp of a stale-only stack must be -1 (no fresh items)");
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack(data), SpoilageState.FRESH));
    }

    @Test
    void rottenStateReturnsMinusOne() {
        // ROTTEN has no timestamps — only a count. Both methods must return -1.
        SpoilageData data = new SpoilageData(List.of(), List.of(), 5, 1.0);
        assertEquals(-1L, FoodSpoilageUtil.getWorstTimestamp(stack(data), SpoilageState.ROTTEN),
                "ROTTEN state has no timestamps — must return -1");
        assertEquals(-1L, FoodSpoilageUtil.getBestTimestamp(stack(data), SpoilageState.ROTTEN));
    }

    @Test
    void singleItemMinAndMaxAreEqual() {
        SpoilageData data = new SpoilageData(List.of(4000L), List.of(), 0, 1.0);
        assertEquals(4000L, FoodSpoilageUtil.getWorstTimestamp(stack(data), SpoilageState.FRESH));
        assertEquals(4000L, FoodSpoilageUtil.getBestTimestamp(stack(data), SpoilageState.FRESH),
                "A single item's worst and best timestamp are the same");
    }

    @Test
    void worstStateOfMixedStackIsStale() {
        // 1 fresh + 1 stale: the WORST state is STALE (fresh expires into stale)
        SpoilageData data = new SpoilageData(List.of(9000L), List.of(3000L), 0, 1.0);
        assertEquals(SpoilageState.STALE, FoodSpoilageUtil.getWorstState(stack(data)),
                "A stack with any stale item has STALE as its worst state");
        assertEquals(SpoilageState.FRESH, FoodSpoilageUtil.getBestState(stack(data)),
                "A stack with any fresh item has FRESH as its best state");
    }

    @Test
    void worstStateOfRottenStackIsRotten() {
        SpoilageData data = new SpoilageData(List.of(), List.of(), 3, 1.0);
        assertEquals(SpoilageState.ROTTEN, FoodSpoilageUtil.getWorstState(stack(data)));
        assertEquals(SpoilageState.ROTTEN, FoodSpoilageUtil.getBestState(stack(data)),
                "An entirely-rotten stack has ROTTEN as both best and worst state");
    }
}