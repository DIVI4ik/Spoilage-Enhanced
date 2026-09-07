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
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 165 regression test: container spoilage trim path (BUG-20).
 *
 * updateContainerItemSpoilage runs every 20 ticks for every container item in every
 * loaded inventory (ItemMixin). Its trim step reduces an over-tracked containerized
 * item (count < totalTracked, reachable via copyWithCount from a hopper/dispenser)
 * to its count keeping the WORST trackers — without it, updateSpoilage would "heal"
 * the item by keeping the BEST trackers, inverting its spoilage.
 *
 * A null Level is used deliberately: updateSpoilage(item, null) returns early, so the
 * trim runs in isolation and the test observes exactly what the trim did. (The
 * full update path needs a real Level, which cannot be constructed in a unit test.)
 *
 * NOTE: With DataComponentMap.EMPTY bound, all items have max_stack_size = 1.
 * All test items use count = 1 to avoid "stack size larger than maximum" warnings.
 */
public class ContainerSpoilageTest {

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

    private static ItemStack apple(int count, SpoilageData data) {
        // Egg is always-spoilable (AutoFoodDetector.isAlwaysSpoilable), so it doesn't
        // depend on the FOOD component being bound — making it the safest test subject
        // in a unit test where components are bound to DataComponentMap.EMPTY.
        ItemStack stack = new ItemStack(Items.EGG, count);
        if (data != null) stack.set(ModDataComponentTypes.SPOILAGE, data);
        return stack;
    }

    private static ItemStack shulkerWith(ItemStack... contents) {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(contents)));
        return shulker;
    }

    private static ItemStack firstSlot(ItemStack shulker) {
        net.minecraft.core.NonNullList<ItemStack> slots =
                net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
        shulker.get(net.minecraft.core.component.DataComponents.CONTAINER).copyInto(slots);
        return slots.get(0);
    }

    @Test
    void containerUpdateTrimsOverTrackedToWorst() {
        // An egg with count 1 but 3 trackers (2 fresh, 1 stale): the trim must keep
        // the WORST tracker (the stale one), not the best (BUG-20 laundering guard).
        // count=1, trackers=3 (2 fresh, 1 stale) -> trim keeps the worst (stale)
        ItemStack overTracked = apple(1, new SpoilageData(
                List.of(9000L, 9500L), List.of(3000L), 0, 1.0));

        ItemStack shulker = shulkerWith(overTracked);
        FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null);

        SpoilageData trimmed = firstSlot(shulker).get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(trimmed, "The containerized item must still have a component");
        assertEquals(1, trimmed.totalTracked(),
                "The trim must reduce trackers to the item count (1)");
        assertEquals(1, trimmed.staleExpirations().size(),
                "The trim must keep the WORST (stale) tracker, not the fresh ones");
        assertTrue(trimmed.freshExpirations().isEmpty(),
                "The fresh trackers must be dropped (they are the BEST, not the worst)");
    }

    @Test
    void containerUpdateTrimsOverTrackedRottenFirst() {
        // count 1, trackers: 1 fresh + 2 rotten. Worst = rotten, so the trim keeps rotten.
        ItemStack overTracked = apple(1, new SpoilageData(
                List.of(9000L), List.of(), 2, 1.0));

        ItemStack shulker = shulkerWith(overTracked);
        FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null);

        SpoilageData trimmed = firstSlot(shulker).get(ModDataComponentTypes.SPOILAGE);
        assertNotNull(trimmed);
        assertEquals(1, trimmed.totalTracked());
        assertEquals(1, trimmed.rottenCount(),
                "Rotten is the worst state — the trim must keep the rotten tracker");
        assertTrue(trimmed.freshExpirations().isEmpty());
    }

    @Test
    void containerUpdateLeavesCorrectlyTrackedAlone() {
        // count 1, trackers 1 (fresh): nothing to trim — the data must be unchanged.
        SpoilageData original = new SpoilageData(List.of(9000L), List.of(), 0, 1.0);
        ItemStack tracked = apple(1, original);

        ItemStack shulker = shulkerWith(tracked);
        FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null);

        SpoilageData after = firstSlot(shulker).get(ModDataComponentTypes.SPOILAGE);
        assertEquals(original, after,
                "A correctly-tracked item must pass through the trim untouched");
    }

    @Test
    void containerUpdateSkipsNonSpoilableContents() {
        ItemStack stone = new ItemStack(Items.STONE, 1);
        ItemStack shulker = shulkerWith(stone);

        FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null);

        assertNull(firstSlot(shulker).get(ModDataComponentTypes.SPOILAGE),
                "A non-spoilable item must not gain a component");
    }

    @Test
    void containerUpdateSkipsNonContainerStack() {
        ItemStack plain = new ItemStack(Items.APPLE, 1);
        // No CONTAINER component — the method must return without touching anything
        assertDoesNotThrow(() -> FoodSpoilageUtil.updateContainerItemSpoilage(plain, null));
        assertNull(plain.get(ModDataComponentTypes.SPOILAGE));
    }

    @Test
    void containerUpdateHandlesEmptyContainer() {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(net.minecraft.core.component.DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertDoesNotThrow(() -> FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null));
    }

    @Test
    void containerUpdateHandlesEmptyStack() {
        assertDoesNotThrow(() -> FoodSpoilageUtil.updateContainerItemSpoilage(ItemStack.EMPTY, null));
    }

    @Test
    void containerUpdateWritesBackOnlyWhenChanged() {
        // A correctly-tracked item with a null world: updateSpoilage returns early, so
        // nothing changes and the CONTAINER component must not be rewritten.
        SpoilageData original = new SpoilageData(List.of(9000L), List.of(), 0, 1.0);
        ItemStack tracked = apple(1, original);
        ItemStack shulker = shulkerWith(tracked);

        ItemContainerContents before = shulker.get(net.minecraft.core.component.DataComponents.CONTAINER);
        FoodSpoilageUtil.updateContainerItemSpoilage(shulker, null);
        ItemContainerContents after = shulker.get(net.minecraft.core.component.DataComponents.CONTAINER);

        assertSame(before, after,
                "With nothing changed the container component must not be rewritten (GC guard)");
    }
}